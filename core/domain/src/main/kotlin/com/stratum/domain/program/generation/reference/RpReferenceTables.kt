@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.reference

import com.stratum.domain.program.generation.context.MINUTES_PER_HARD_SET
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT

/** Max hard sets per muscle per workout before junk-volume territory (Stratum default). */
const val SESSION_MUSCLE_SET_CAP = 8

/** Max hard sets on a single exercise in one session — avoids repetitive high-volume stacks. */
const val SETS_PER_LIFT_CAP = 5

/** Hard-set cap for one workout from stated session length (3 min per set). */
fun sessionHardSetCapForLength(sessionLengthMinutes: Int?): Int? {
    if (sessionLengthMinutes == null || sessionLengthMinutes <= 0) return null
    return kotlin.math.floor(sessionLengthMinutes / MINUTES_PER_HARD_SET).toInt()
}

/** Duration budget in minutes for validation (matches the user's stated session length). */
fun sessionDurationBudgetMinutes(sessionLengthMinutes: Int): Int = sessionLengthMinutes

enum class FrequencyTier(val value: Int) {
    One(1),
    Two(2),
    Three(3),
    ;

    companion object {
        fun fromWeeklySets(weeklySets: Double): FrequencyTier = frequencyTierForWeeklySets(weeklySets)
    }
}

/** TABLE 7.6 — weekly fractional sets → minimum frequency tier. */
fun frequencyTierForWeeklySets(weeklySets: Double): FrequencyTier =
    when {
        weeklySets <= 10 -> FrequencyTier.One
        weeklySets <= 20 -> FrequencyTier.Two
        else -> FrequencyTier.Three
    }

data class VolumeTimeTier(
    val setsMin: Int,
    val setsMax: Int,
    val label: String,
    val hoursMin: Int,
    val hoursMax: Double,
)

/** TABLE 7.4 volume tiers by weekly sets per muscle (approximate). */
val VOLUME_TIME_TIERS: List<VolumeTimeTier> = listOf(
    VolumeTimeTier(setsMin = 4, setsMax = 8, label = "Minimal", hoursMin = 1, hoursMax = 2.5),
    VolumeTimeTier(setsMin = 9, setsMax = 12, label = "Low", hoursMin = 3, hoursMax = 4.5),
    VolumeTimeTier(setsMin = 13, setsMax = 16, label = "Medium", hoursMin = 5, hoursMax = 6.5),
    VolumeTimeTier(setsMin = 17, setsMax = 20, label = "High", hoursMin = 7, hoursMax = 8.5),
    VolumeTimeTier(setsMin = 21, setsMax = 30, label = "Very High", hoursMin = 9, hoursMax = 14.0),
)

enum class RpRepProfile {
    LowerFreeWeightCompound,
    LowerMachineOrUpperPress,
    UpperMachineOrPull,
    Isolation,
}

data class RpRepRirBand(
    val repMin: Int,
    val repMax: Int,
    val topSetRir: Int,
    val backOffRirMin: Int,
    val backOffRirMax: Int,
)

/** TABLE 7.7 rep/RIR exercise classes. */
val RP_REP_RIR_BANDS: Map<RpRepProfile, RpRepRirBand> = mapOf(
    RpRepProfile.LowerFreeWeightCompound to
        RpRepRirBand(repMin = 4, repMax = 8, topSetRir = 4, backOffRirMin = 2, backOffRirMax = 4),
    RpRepProfile.LowerMachineOrUpperPress to
        RpRepRirBand(repMin = 4, repMax = 12, topSetRir = 3, backOffRirMin = 1, backOffRirMax = 3),
    RpRepProfile.UpperMachineOrPull to
        RpRepRirBand(repMin = 6, repMax = 15, topSetRir = 2, backOffRirMin = 0, backOffRirMax = 2),
    RpRepProfile.Isolation to
        RpRepRirBand(repMin = 8, repMax = 20, topSetRir = 1, backOffRirMin = 0, backOffRirMax = 1),
)

data class PatternSecondaryMuscleCredit(
    val muscle: String,
    val creditPerSet: Double,
)

/** Typical secondary muscle credits by movement pattern (slot-level estimate during allocation). */
val PATTERN_SECONDARY_MUSCLE_CREDITS: Map<String, List<PatternSecondaryMuscleCredit>> = mapOf(
    "SquatPattern" to listOf(PatternSecondaryMuscleCredit("Glute", SECONDARY_SET_CREDIT)),
    "HipHinge" to
        listOf(
            PatternSecondaryMuscleCredit("Glute", SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit("LowerBack", SECONDARY_SET_CREDIT),
        ),
    "HipExtension" to listOf(PatternSecondaryMuscleCredit("Hamstring", SECONDARY_SET_CREDIT)),
    "VerticalPull" to listOf(PatternSecondaryMuscleCredit("Bicep", SECONDARY_SET_CREDIT)),
    "HorizontalPull" to
        listOf(
            PatternSecondaryMuscleCredit("Bicep", SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit("PosteriorDeltoid", SECONDARY_SET_CREDIT),
        ),
    "HorizontalPress" to
        listOf(
            PatternSecondaryMuscleCredit("Tricep", SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit("AnteriorDeltoid", SECONDARY_SET_CREDIT),
        ),
    "InclinePress" to
        listOf(
            PatternSecondaryMuscleCredit("Tricep", SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit("AnteriorDeltoid", SECONDARY_SET_CREDIT),
        ),
    "VerticalPress" to
        listOf(
            PatternSecondaryMuscleCredit("Tricep", SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit("AnteriorDeltoid", SECONDARY_SET_CREDIT),
        ),
)

/** Minimum direct (primary) sets for a specialized muscle's isolation slot in one session. */
const val MIN_SPECIALIZED_DIRECT_SETS = 4

/** Minimum direct sets for a cross-domain touch isolation slot (supplementary volume). */
const val MIN_TOUCH_DIRECT_SETS = 2
