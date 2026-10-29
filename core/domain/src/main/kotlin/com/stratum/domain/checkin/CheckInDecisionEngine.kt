package com.stratum.domain.checkin

import com.stratum.domain.dashboard.CALLOUT_MIN_POINTS
import com.stratum.domain.dashboard.CALLOUT_MIN_R_SQUARED
import com.stratum.domain.dashboard.LIFT_CALLOUT_MIN_SESSIONS
import com.stratum.domain.dashboard.LIFT_CALLOUT_SLOPE_THRESHOLD
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.olsFit
import kotlin.math.abs
import kotlin.math.roundToInt
import com.stratum.domain.onboarding.MuscleGroup

/** Weeks on the same variation before a plateaued / soft lift is considered stale (swap path). */
const val STALE_LIFT_TENURE_WEEKS = 4

/** Working-set cut fraction for severe under-recovery / joint pain (~25%). */
const val VOLUME_CUT_FRACTION = 0.25

/** Sets added on a soft-fresh (or failed-swap) volume bump. */
const val CHECKIN_VOLUME_STEP = 1

/**
 * Pure deterministic coach: hard performance is primary; subjective recovery is secondary.
 *
 * Trend classification uses the **same high bar as dashboard lift callouts** (slope, R², point
 * count, session count) so check-in does not fire on short noisy windows.
 *
 * Rule 1: Climbing → Maintain (even with mild soreness).
 * Rule 2a: Dropping + mild soreness → Maintain (do not add stress).
 * Rule 2b: Flat with good/mild recovery, or Dropping with good recovery:
 *   - stale (≥4 weeks) → swap kind
 *   - fresh + at volume ceiling → Maintain
 *   - fresh + under ceiling → +1 set
 * Rule 3: Severe under-recovery **or** joint pain → cut volume ~25% (strongly recommended;
 * user still accept/rejects). Insufficient trend evidence → Maintain.
 *
 * Multi-lift one-lever selection is [CheckInBatchSelector]. Swap catalog enrichment is
 * [EvaluateCheckInUseCase]; this engine only emits [CheckInActionKind.SwapLift] without lift ids.
 */
object CheckInDecisionEngine {

    fun classifyPerformanceTrend(
        performanceSeries: List<TrendPoint>,
        sessionCount: Int = LIFT_CALLOUT_MIN_SESSIONS,
        slopeThreshold: Double = LIFT_CALLOUT_SLOPE_THRESHOLD,
        minPoints: Int = CALLOUT_MIN_POINTS,
        minRSquared: Double = CALLOUT_MIN_R_SQUARED,
        minSessions: Int = LIFT_CALLOUT_MIN_SESSIONS,
    ): PerformanceTrend {
        if (sessionCount < minSessions || performanceSeries.size < minPoints) {
            return PerformanceTrend.Insufficient
        }
        val fit = olsFit(performanceSeries)
        if (fit == null) return PerformanceTrend.Flat
        if (abs(fit.slope) < slopeThreshold) return PerformanceTrend.Flat
        if (fit.rSquared < minRSquared) return PerformanceTrend.Insufficient
        return if (fit.slope > 0) PerformanceTrend.Climbing else PerformanceTrend.Dropping
    }

    fun resolveRecoverySignal(
        overall: RecoverySignal,
        muscleSignals: Map<com.stratum.domain.onboarding.MuscleGroup, RecoverySignal>,
        primaryMuscles: Set<com.stratum.domain.onboarding.MuscleGroup>,
    ): RecoverySignal {
        val local =
            primaryMuscles
                .mapNotNull { muscleSignals[it] }
                .maxByOrNull { it.severity }
                ?: RecoverySignal.Good
        return listOf(overall, local).maxBy { it.severity }
    }

    fun decide(input: LiftCheckInInput): CheckInCoreDecision {
        val severeRecovery =
            input.recoverySignal == RecoverySignal.SevereUnderRecovery ||
                input.recoverySignal == RecoverySignal.JointPain
        return when {
            severeRecovery -> decideSevereRecoveryCut(input)
            input.performanceTrend == PerformanceTrend.Climbing ->
                CheckInCoreDecision(
                    kind = CheckInActionKind.Maintain,
                    rationale = CheckInRationale.ClimbingMaintain,
                )
            input.performanceTrend == PerformanceTrend.Insufficient ->
                CheckInCoreDecision(
                    kind = CheckInActionKind.Maintain,
                    rationale = CheckInRationale.NoClearChange,
                )
            input.performanceTrend == PerformanceTrend.Dropping &&
                input.recoverySignal == RecoverySignal.MildSoreness ->
                CheckInCoreDecision(
                    kind = CheckInActionKind.Maintain,
                    rationale = CheckInRationale.DroppingMildRecoveryHold,
                )
            isSoftPerformanceWithOkRecovery(input) -> decideSoftLever(input)
            else ->
                CheckInCoreDecision(
                    kind = CheckInActionKind.Maintain,
                    rationale = CheckInRationale.NoClearChange,
                )
        }
    }

    private fun decideSevereRecoveryCut(input: LiftCheckInInput): CheckInCoreDecision {
        val remove = setsToRemove(input.currentSetCount)
        if (remove == 0) {
            return CheckInCoreDecision(
                kind = CheckInActionKind.CutVolume,
                removeSets = 0,
                rationale = CheckInRationale.AlreadyAtFloorSets,
            )
        }
        val rationale =
            if (input.recoverySignal == RecoverySignal.JointPain) {
                CheckInRationale.JointPainCut(remove)
            } else {
                CheckInRationale.SevereUnderRecoveryCut(remove)
            }
        return CheckInCoreDecision(
            kind = CheckInActionKind.CutVolume,
            removeSets = remove,
            rationale = rationale,
        )
    }

    private fun decideSoftLever(input: LiftCheckInInput): CheckInCoreDecision {
        // Soft levers need primary-muscle identity for ceiling + one-lever batching.
        if (input.primaryMuscles.isEmpty()) {
            return CheckInCoreDecision(
                kind = CheckInActionKind.Maintain,
                rationale = CheckInRationale.NoClearChange,
            )
        }
        val soft = input.performanceTrend.toSoftTrend()
        return when {
            input.rotationTenureWeeks >= STALE_LIFT_TENURE_WEEKS ->
                CheckInCoreDecision(
                    kind = CheckInActionKind.SwapLift,
                    rationale =
                        CheckInRationale.SoftStaleSwap(
                            softTrend = soft,
                            tenureWeeks = input.rotationTenureWeeks,
                        ),
                )
            input.atVolumeCeiling ->
                CheckInCoreDecision(
                    kind = CheckInActionKind.Maintain,
                    rationale = CheckInRationale.SoftFreshAtVolumeCeiling,
                )
            else ->
                CheckInCoreDecision(
                    kind = CheckInActionKind.IncreaseVolume,
                    addSets = CHECKIN_VOLUME_STEP,
                    rationale =
                        CheckInRationale.SoftFreshIncrease(
                            softTrend = soft,
                            tenureWeeks = input.rotationTenureWeeks,
                        ),
                )
        }
    }

    fun setsToRemove(currentSetCount: Int): Int {
        if (currentSetCount <= 1) return 0
        val raw = (currentSetCount * VOLUME_CUT_FRACTION).roundToInt().coerceAtLeast(1)
        return raw.coerceAtMost(currentSetCount - 1)
    }

    /**
     * Soft path eligible for swap / +1: Flat with good or mild recovery, or Dropping with good
     * recovery only (dropping + mild is held separately).
     */
    fun isSoftPerformanceWithOkRecovery(input: LiftCheckInInput): Boolean {
        val recoveryOk =
            input.recoverySignal == RecoverySignal.Good ||
                input.recoverySignal == RecoverySignal.MildSoreness
        return when (input.performanceTrend) {
            PerformanceTrend.Flat -> recoveryOk
            PerformanceTrend.Dropping -> input.recoverySignal == RecoverySignal.Good
            else -> false
        }
    }

    fun recommendationComparator(): Comparator<CheckInRecommendation> =
        compareBy<CheckInRecommendation> { it.action.sortPriority() }
            .thenByDescending { it.performanceTrend == PerformanceTrend.Dropping }
            .thenBy { it.liftName.lowercase() }

    private fun PerformanceTrend.toSoftTrend(): SoftPerformanceTrend =
        when (this) {
            PerformanceTrend.Dropping -> SoftPerformanceTrend.Dropping
            PerformanceTrend.Flat -> SoftPerformanceTrend.Flat
            PerformanceTrend.Climbing,
            PerformanceTrend.Insufficient,
            -> error("toSoftTrend only for Flat/Dropping, got $this")
        }

    private val RecoverySignal.severity: Int
        get() =
            when (this) {
                RecoverySignal.Good -> 0
                RecoverySignal.MildSoreness -> 1
                RecoverySignal.SevereUnderRecovery -> 2
                RecoverySignal.JointPain -> 3
            }
}
