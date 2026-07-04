@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.context

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
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
    val trainingDaysPerWeek: Int,
    val weekdays: List<String>,
    val specialization: Map<MuscleGroup, SpecializationLevel>,
    val liftsById: Map<String, LiftContext>,
    val muscleVolumeSnapshotsByMuscle: Map<String, MuscleVolumeSnapshotContext>,
    val volumeCorrelationsByMuscle: Map<String, VolumeCorrelationContext>,
    val enabledLiftIds: Set<String>,
    val movementPatternByLiftId: Map<String, MovementPattern?>,
    /** Per-lift effectiveness rank/evidence from responseProfile.liftEffectiveness, keyed by liftId. */
    val liftEffectivenessByLiftId: Map<String, LiftEffectivenessEntry>,
)

private val PUSH_PATTERNS = setOf(MovementPattern.HorizontalPress, MovementPattern.VerticalPress, MovementPattern.InclinePress)
private val PULL_PATTERNS = setOf(MovementPattern.HorizontalPull, MovementPattern.VerticalPull)

val MAJOR_MUSCLES: Set<MuscleGroup> = setOf(
    MuscleGroup.Chest,
    MuscleGroup.Back,
    MuscleGroup.Quad,
    MuscleGroup.Hamstring,
    MuscleGroup.Glute,
    MuscleGroup.Tricep,
    MuscleGroup.Bicep,
    MuscleGroup.LateralDeltoid,
    MuscleGroup.PosteriorDeltoid,
    MuscleGroup.AnteriorDeltoid,
)

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
