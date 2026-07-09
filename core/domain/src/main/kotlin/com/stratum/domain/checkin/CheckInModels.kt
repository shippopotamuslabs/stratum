package com.stratum.domain.checkin

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant

/**
 * Persisted coach check-in preferences (DataStore). Pending state is set only after a successful
 * response-modeling **re-extract** (≥20 new workouts), never on first-ever extract.
 */
data class CheckInPreferences(
    val enabled: Boolean = true,
    val pendingSinceExtractedAt: Instant? = null,
    val lastCompletedAt: Instant? = null,
    val lastSkippedAt: Instant? = null,
) {
    val isPending: Boolean
        get() = enabled && pendingSinceExtractedAt != null
}

/**
 * Hard-data performance classification. Climbing/Dropping only when the series clears the same
 * gates as dashboard lift callouts (points, sessions, slope, R²); otherwise Insufficient or Flat.
 */
enum class PerformanceTrend {
    Climbing,
    Flat,
    Dropping,
    Insufficient,
}

/**
 * Subjective recovery signal for a muscle (or overall). Joint pain and severe under-recovery
 * force a volume cut regardless of mild performance noise.
 */
enum class RecoverySignal {
    Good,
    MildSoreness,
    SevereUnderRecovery,
    JointPain,
}

/** Ephemeral subjective answers collected during the check-in (not persisted in v1). */
data class CheckInSubjectiveFeedback(
    val overallRecovery: RecoverySignal = RecoverySignal.Good,
    val muscleSignals: Map<MuscleGroup, RecoverySignal> = emptyMap(),
)

/** Soft performance subset used in typed rationales (flat or dropping with OK recovery). */
enum class SoftPerformanceTrend {
    Flat,
    Dropping,
}

/**
 * Pure decision kind from the engine (no catalog enrichment). Swap has no lift ids yet —
 * [EvaluateCheckInUseCase] attaches candidates or falls back to volume.
 */
enum class CheckInActionKind {
    Maintain,
    SwapLift,
    IncreaseVolume,
    CutVolume,
}

/**
 * Machine-readable reason for a recommendation. Presentation maps these to string resources —
 * domain does not own English product copy.
 */
sealed interface CheckInRationale {
    data object ClimbingMaintain : CheckInRationale

    data object NoClearChange : CheckInRationale

    data class SoftStaleSwap(
        val softTrend: SoftPerformanceTrend,
        val tenureWeeks: Int,
    ) : CheckInRationale

    data class SoftFreshIncrease(
        val softTrend: SoftPerformanceTrend,
        val tenureWeeks: Int,
    ) : CheckInRationale

    /** Stale soft lift but no valid non-excluded swap candidate — add volume instead. */
    data class SoftStaleIncreaseNoCandidate(
        val softTrend: SoftPerformanceTrend,
        val tenureWeeks: Int,
    ) : CheckInRationale

    data class JointPainCut(val removeSets: Int) : CheckInRationale

    data class SevereUnderRecoveryCut(val removeSets: Int) : CheckInRationale

    /**
     * Severe recovery/joint pain but lift already at 1 set — no template set cut is possible.
     * UI should say manage load in-session, not claim a deload was applied.
     */
    data object AlreadyAtFloorSets : CheckInRationale
}

/** Core engine output before swap catalog enrichment. */
data class CheckInCoreDecision(
    val kind: CheckInActionKind,
    val addSets: Int = 0,
    val removeSets: Int = 0,
    val rationale: CheckInRationale,
)

/** Fully resolved action after evaluate enrichment (swap always has a concrete lift id). */
sealed interface CheckInAction {
    data object Maintain : CheckInAction

    data class SwapLift(
        val suggestedLiftId: LiftId,
        val suggestedLiftName: String,
    ) : CheckInAction

    data class IncreaseVolume(val addSets: Int = 1) : CheckInAction

    /** [removeSets] == 0 means already at floor (no set mutation). */
    data class CutVolume(val removeSets: Int) : CheckInAction
}

/** One lift-level recommendation from evaluation (immutable; accept/reject is presentation-only). */
data class CheckInRecommendation(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val liftName: String,
    val primaryMuscles: Set<MuscleGroup>,
    val performanceTrend: PerformanceTrend,
    val recoverySignal: RecoverySignal,
    val rotationTenureWeeks: Int,
    val currentSetCount: Int,
    val action: CheckInAction,
    val rationale: CheckInRationale,
)

/** Full evaluation result for a check-in session. */
data class CheckInEvaluation(
    val evaluatedAt: Instant,
    val recommendations: List<CheckInRecommendation>,
    /** Active program lift ids (for swap-picker exclusion). */
    val programLiftIds: Set<LiftId> = emptySet(),
) {
    val actionable: List<CheckInRecommendation>
        get() = recommendations.filter { it.action !is CheckInAction.Maintain }
}

/** Inputs for the pure decision engine for a single lift (no swap suggestions). */
data class LiftCheckInInput(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val liftName: String,
    val primaryMuscles: Set<MuscleGroup>,
    val performanceTrend: PerformanceTrend,
    val recoverySignal: RecoverySignal,
    val rotationTenureWeeks: Int,
    val currentSetCount: Int,
)

/**
 * Atomic apply commands built from accepted presentation rows. Absolute set targets avoid
 * non-idempotent delta retries after a partial failure.
 */
sealed interface CheckInApplyCommand {
    val workoutLiftId: WorkoutLiftId

    data class Swap(
        override val workoutLiftId: WorkoutLiftId,
        val newLiftId: LiftId,
    ) : CheckInApplyCommand

    data class SetWorkingSets(
        override val workoutLiftId: WorkoutLiftId,
        val targetSetCount: Int,
    ) : CheckInApplyCommand
}
