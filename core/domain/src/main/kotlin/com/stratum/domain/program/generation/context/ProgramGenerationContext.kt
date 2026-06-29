@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.context

enum class MuscleLengthBias {
    Lengthened,
    Shortened,
    MidRange,
}

data class LiftContext(
    val id: String,
    val name: String,
    val movementPattern: String,
    val movementPatterns: List<String>,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
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
)

enum class VolumeLandmarkSource {
    Personal,
    Literature,
}

data class VolumeLandmarkContext(
    val muscle: String,
    val estimatedMev: Double,
    val estimatedMav: Double,
    val estimatedMrv: Double,
    val estimatedPv: Double,
    val source: VolumeLandmarkSource,
)

data class VolumeResponseContext(
    val muscle: String,
    val correlationSlope: Double,
    val evidence: String,
)

data class LiftEffectivenessEntry(
    val liftId: String,
    val pattern: String,
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
    val specialization: Map<String, String>,
    val liftsById: Map<String, LiftContext>,
    val volumeLandmarksByMuscle: Map<String, VolumeLandmarkContext>,
    val volumeResponsesByMuscle: Map<String, VolumeResponseContext>,
    val enabledLiftIds: Set<String>,
    val movementPatternByLiftId: Map<String, String>,
    /** Per-lift effectiveness rank/evidence from responseProfile.liftEffectiveness, keyed by liftId. */
    val liftEffectivenessByLiftId: Map<String, LiftEffectivenessEntry>,
)

private val PUSH_PATTERNS = setOf("HorizontalPress", "VerticalPress", "InclinePress")
private val PULL_PATTERNS = setOf("HorizontalPull", "VerticalPull")

val MAJOR_MUSCLES: List<String> = listOf(
    "Chest",
    "Back",
    "Quad",
    "Hamstring",
    "Glute",
    "Tricep",
    "Bicep",
    "LateralDeltoid",
    "PosteriorDeltoid",
    "AnteriorDeltoid",
)

const val SECONDARY_SET_CREDIT = 0.5
const val PRIMARY_SET_CREDIT = 1.0
const val MINUTES_PER_HARD_SET = 3.5

fun isPushPattern(pattern: String): Boolean = pattern in PUSH_PATTERNS

fun isPullPattern(pattern: String): Boolean = pattern in PULL_PATTERNS

data class NormalizationRequest(
    val trainingAge: String,
    val enabledLiftIds: Set<String>,
    val movementPatternByLiftId: Map<String, String>,
)

fun ProgramGenerationContext.toNormalizationRequest(): NormalizationRequest =
    NormalizationRequest(
        trainingAge = trainingAge,
        enabledLiftIds = enabledLiftIds,
        movementPatternByLiftId = movementPatternByLiftId,
    )
