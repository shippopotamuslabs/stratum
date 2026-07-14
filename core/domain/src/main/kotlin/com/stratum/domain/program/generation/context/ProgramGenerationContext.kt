@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.context

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel

enum class MuscleLengthBias {
    Lengthened,
    Shortened,
    MidRange,
}

data class LiftContext(
    val id: String,
    val name: String,
    val movementPattern: MovementPattern?,
    val movementPatterns: Set<MovementPattern>,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup>,
    val mainstream: Boolean? = null,
    val progressTrend: String? = null,
    /** Whether the lift is in the proven tier (matched from logged history) vs the available pool. */
    val proven: Boolean? = null,
    /** Number of logged sessions for this lift (from trainingInsights); 0 when no insights. */
    val sessionCount: Int? = null,
    /** Within-pattern effectiveness rank (1 = best) from responseProfile.liftEffectiveness; undefined if none. */
    val effectivenessRank: Int? = null,
    /** Effectiveness evidence tier from responseProfile.liftEffectiveness. */
    val effectivenessEvidence: String? = null,
    /** Within-session fatigue decay slope from trainingInsights (steeper = faster fatigue). */
    val fatigueDecaySlope: Double? = null,
    /** Within-session fatigue decay intercept from trainingInsights. */
    val fatigueDecayIntercept: Double? = null,
    val muscleLengthBias: MuscleLengthBias? = null,
    val equipment: String? = null,
    /** Loading category — drives recovery classification (AxialCompound/NonAxialCompound/Isolation). */
    val kinematicProfile: KinematicProfile = KinematicProfile.Isolation,
    /**
     * Weight-banded increment steps (from the [com.stratum.domain.lift.Lift]). Drives the
     * rep-range *width* via the increment:typical-load ratio — a small increment on a light
     * isolation lift shifts intensity more per jump than a large increment on a heavy compound,
     * so the former carries a wider practical progression band. Empty when the lift has no
     * tier data, in which case generation falls back to the static [RP_REP_RIR_BANDS] spread.
     */
    val incrementTiers: List<IncrementTier> = emptyList(),
    /**
     * Estimated one-rep max from training insights (reps+RIR against the Brzycki/exponential
     * engine). Used as the typical-load anchor for the increment:typical-load ratio: the
     * per-increment rep shift is computed at `weightForReps(e1rm, 10)`. Null when no proven
     * history exists, in which case generation falls back to the static band width.
     */
    val estimatedOneRm: Weight? = null,
)

enum class VolumeSnapshotSource {
    Personal,
    Literature,
}

/** Per-muscle MV / PV / RC resolved for program generation (population and/or import). */
data class MuscleVolumeSnapshotContext(
    val muscle: String,
    val retentionFloorMv: Double,
    val paretoVolumePv: Double,
    val recoverabilityCeilingRc: Double,
    val growthEntryFloor: Double,
    val maintenanceCeiling: Double,
    val posture: String?,
    val observedDoseMax: Double? = null,
    val observedDoseMedian: Double? = null,
    val source: VolumeSnapshotSource,
)
data class VolumeCorrelationContext(
    val muscle: String,
    val correlationSlope: Double,
    val evidence: String,
)

data class LiftEffectivenessEntry(
    val liftId: String,
    val pattern: MovementPattern,
    val rank: Int,
    val effectivenessScore: Double? = null,
    val evidence: String,
)

data class ProgramGenerationContext(
    val trainingAge: String,
    val lastTrained: String?,
    val sessionLengthMinutes: Int?,
    /**
     * Training session count for this gen (N). Source of truth with [restSlotIndices] /
     * [cycleLength].
     */
    val trainingDaysPerWeek: Int,
    val specialization: Map<MuscleGroup, SpecializationLevel>,
    val liftsById: Map<String, LiftContext>,
    val muscleVolumeSnapshotsByMuscle: Map<String, MuscleVolumeSnapshotContext>,
    val volumeCorrelationsByMuscle: Map<String, VolumeCorrelationContext>,
    val enabledLiftIds: Set<String>,
    val movementPatternByLiftId: Map<String, MovementPattern?>,
    /** Per-lift effectiveness rank/evidence from responseProfile.liftEffectiveness, keyed by liftId. */
    val liftEffectivenessByLiftId: Map<String, LiftEffectivenessEntry>,
    /**
     * Optional raw UX rest-slot pattern. With [cycleLength], this is schedule identity for gen.
     * Nullable — defaults apply only in [materializeGeneratedCycleSpace].
     * Indices are in `[0, cycleLength)`.
     */
    val restSlotIndices: Set<Int>? = null,
    /**
     * Optional raw UX full cycle length (train + rest) paired with [restSlotIndices]. For 6-day
     * gen, UX may send 8 (rotating) or 7 (fixed week). When both this and [restSlotIndices] are
     * null, defaults apply only in [materializeGeneratedCycleSpace].
     */
    val cycleLength: Int? = null,
)

private val PUSH_PATTERNS = setOf(MovementPattern.HorizontalPress, MovementPattern.VerticalPress, MovementPattern.InclinePress)
private val PULL_PATTERNS = setOf(MovementPattern.HorizontalPull, MovementPattern.VerticalPull)

/**
 * Always-on **weekly volume aim** surface for program generation (MV/PV/RC targets and
 * CP-SAT aim/MEV soft costs).
 *
 * Deliberately excludes:
 * - **Glute** — usually covered by squat/hinge secondaries; dedicated aim only when specialized
 * - **Calf** — same; optional isolation unless the user specializes calves
 * - Trap, Forearm, Adductor, Ab, LowerBack, anterior/posterior delts — secondary/incidental only
 *
 * [ALWAYS_AIM_MUSCLES] is the default set. [OPTIONAL_AIM_WHEN_SPECIALIZED] are added only when
 * specialization is Specialize or MinimumVolume (see volume [targetableMuscles]).
 */
val ALWAYS_AIM_MUSCLES: Set<MuscleGroup> = setOf(
    MuscleGroup.Chest,
    MuscleGroup.Back,
    MuscleGroup.Bicep,
    MuscleGroup.Tricep,
    MuscleGroup.LateralDeltoid,
    MuscleGroup.Quad,
    MuscleGroup.Hamstring,
)

/** Opt-in aim muscles — only when the user marks Specialize / MinimumVolume. */
val OPTIONAL_AIM_WHEN_SPECIALIZED: Set<MuscleGroup> = setOf(
    MuscleGroup.Calf,
    MuscleGroup.Glute,
)

/**
 * Historical name used by validation and volume helpers. Equals the default aim surface
 * ([ALWAYS_AIM_MUSCLES]); optional aims are layered in [targetableMuscles], not here.
 */
val MAJOR_MUSCLES: Set<MuscleGroup> = ALWAYS_AIM_MUSCLES

/** @see ALWAYS_AIM_MUSCLES */
val CORE_VOLUME_AIM_MUSCLES: Set<MuscleGroup> = ALWAYS_AIM_MUSCLES

const val SECONDARY_SET_CREDIT = 0.5
const val PRIMARY_SET_CREDIT = 1.0
/** Wall-clock budget per working set (lift, rest, load changes) for session-cap math. */
const val MINUTES_PER_HARD_SET = 3.0

fun isPushPattern(pattern: MovementPattern): Boolean = pattern in PUSH_PATTERNS

fun isPullPattern(pattern: MovementPattern): Boolean = pattern in PULL_PATTERNS

data class NormalizationRequest(
    val trainingAge: String,
    val enabledLiftIds: Set<String>,
    val movementPatternByLiftId: Map<String, MovementPattern?>,
)

fun ProgramGenerationContext.toNormalizationRequest(): NormalizationRequest =
    NormalizationRequest(
        trainingAge = trainingAge,
        enabledLiftIds = enabledLiftIds,
        movementPatternByLiftId = movementPatternByLiftId,
    )
