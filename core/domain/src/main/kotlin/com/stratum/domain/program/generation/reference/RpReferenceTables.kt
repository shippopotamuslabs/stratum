@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.reference

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.MINUTES_PER_HARD_SET
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT

/** Max hard sets per muscle per workout before junk-volume territory (Stratum default). */
const val SESSION_MUSCLE_SET_CAP = 11

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

/**
 * TABLE 7.7 rep/RIR exercise classes — Stratum-tuned.
 *
 * Two adjustments from the raw RP table:
 *
 * 1. **Hypertrophic floor (repMin ≥ 8).** These bands land on the *top sets* of lifts. With a
 *    typical 4-set scheme and ~2-rep/set fatigue decay (POPULATION_FATIGUE_DECAY_SLOPE = -2),
 *    a floor of 4 would put the last set at ~5 reps — the strength/hypertrophy boundary.
 *    Raising the floor to 8 keeps every set in the set across the whole top-set range
 *    hypertrophic. When the original floor was below 8, the spread is retained by bumping the
 *    ceiling by the same delta; when the original was already ≥ 8 (Isolation), the spread is
 *    narrowed to bound the width (see point 2).
 *
 * 2. **Increment-relative width.** The rep-range spread reflects how much a single load
 *    increment shifts intensity relative to the lift's typical load: a heavy barbell where 5 lb
 *    is a small fraction of the load can hold a *narrower* practical range than a dumbbell curl
 *    where one increment jumps intensity a lot. Per-lift increment tiers and typical-load
 *    fields don't exist yet, so the spread is tiered by KinematicProfile (the only per-lift
 *    loading signal available at generation time): AxialCompound = 4, NonAxialCompound = 6,
 *    Isolation = 10. Spread strictly increases along the compound→isolation gradient.
 *
 * RIR fields are unchanged from the source table.
 */
val RP_REP_RIR_BANDS: Map<RpRepProfile, RpRepRirBand> = mapOf(
    RpRepProfile.LowerFreeWeightCompound to
        RpRepRirBand(repMin = 8, repMax = 12, topSetRir = 4, backOffRirMin = 2, backOffRirMax = 4),
    RpRepProfile.LowerMachineOrUpperPress to
        RpRepRirBand(repMin = 8, repMax = 14, topSetRir = 3, backOffRirMin = 1, backOffRirMax = 3),
    RpRepProfile.UpperMachineOrPull to
        RpRepRirBand(repMin = 8, repMax = 14, topSetRir = 2, backOffRirMin = 0, backOffRirMax = 2),
    RpRepProfile.Isolation to
        RpRepRirBand(repMin = 8, repMax = 18, topSetRir = 1, backOffRirMin = 0, backOffRirMax = 1),
)

data class PatternSecondaryMuscleCredit(
    val muscle: MuscleGroup,
    val creditPerSet: Double,
)

/** Typical secondary muscle credits by movement pattern (slot-level estimate during allocation). */
val PATTERN_SECONDARY_MUSCLE_CREDITS: Map<MovementPattern, List<PatternSecondaryMuscleCredit>> = mapOf(
    MovementPattern.SquatPattern to listOf(PatternSecondaryMuscleCredit(MuscleGroup.Glute, SECONDARY_SET_CREDIT)),
    MovementPattern.HipHinge to
        listOf(
            PatternSecondaryMuscleCredit(MuscleGroup.Glute, SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit(MuscleGroup.Hamstring, SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit(MuscleGroup.LowerBack, SECONDARY_SET_CREDIT),
        ),
    MovementPattern.HipExtension to listOf(PatternSecondaryMuscleCredit(MuscleGroup.Hamstring, SECONDARY_SET_CREDIT)),
    MovementPattern.VerticalPull to listOf(PatternSecondaryMuscleCredit(MuscleGroup.Bicep, SECONDARY_SET_CREDIT)),
    MovementPattern.HorizontalPull to
        listOf(
            PatternSecondaryMuscleCredit(MuscleGroup.Bicep, SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit(MuscleGroup.PosteriorDeltoid, SECONDARY_SET_CREDIT),
        ),
    MovementPattern.HorizontalPress to
        listOf(
            PatternSecondaryMuscleCredit(MuscleGroup.Tricep, SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit(MuscleGroup.AnteriorDeltoid, SECONDARY_SET_CREDIT),
        ),
    MovementPattern.InclinePress to
        listOf(
            PatternSecondaryMuscleCredit(MuscleGroup.Tricep, SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit(MuscleGroup.AnteriorDeltoid, SECONDARY_SET_CREDIT),
        ),
    MovementPattern.VerticalPress to
        listOf(
            PatternSecondaryMuscleCredit(MuscleGroup.Tricep, SECONDARY_SET_CREDIT),
            PatternSecondaryMuscleCredit(MuscleGroup.AnteriorDeltoid, SECONDARY_SET_CREDIT),
        ),
)

/**
 * Computes per-pattern secondary muscle credits from the **actual enabled lift catalog**, averaging
 * the secondary muscle tags across all enabled lifts that share each movement pattern. This makes the
 * solver's volume accounting match the *expected realized* metadata volume (what the Programs UI shows)
 * rather than a static idealization.
 *
 * Without this, patterns like `HipHinge` systematically undercount muscles that only some lifts tag.
 * For example, `barbell-sumo-deadlift` (HipHinge) tags Quad as secondary, but the static table credits
 * 0 Quad for HipHinge — so the solver allocates HipHinge sets thinking they cost 0 Quad, then the UI
 * shows Quad volume inflated by the secondary credit the solver never saw.
 *
 * Falls back to [PATTERN_SECONDARY_MUSCLE_CREDITS] for any pattern with no enabled lifts, preserving
 * the idealized estimate when the catalog provides no signal.
 */
fun computePatternSecondaryCredits(
    context: ProgramGenerationContext,
): Map<MovementPattern, List<PatternSecondaryMuscleCredit>> {
    val liftsByPattern = mutableMapOf<MovementPattern, MutableList<LiftContext>>()
    for (lift in context.liftsById.values) {
        if (lift.id !in context.enabledLiftIds) continue
        for (pattern in lift.movementPatterns) {
            liftsByPattern.getOrPut(pattern) { mutableListOf() }.add(lift)
        }
    }

    val result = mutableMapOf<MovementPattern, List<PatternSecondaryMuscleCredit>>()
    for ((pattern, lifts) in liftsByPattern) {
        if (lifts.isEmpty()) continue
        val count = lifts.size.toDouble()
        // Sum per-muscle: each lift contributes SECONDARY_SET_CREDIT for each secondary muscle
        // (excluding its own primaries, matching computeWorkoutMuscleVolume / AntagonistMetadataBalance).
        val muscleTotals = mutableMapOf<MuscleGroup, Double>()
        for (lift in lifts) {
            for (muscle in lift.secondaryMuscles) {
                if (muscle in lift.primaryMuscles) continue
                muscleTotals[muscle] = (muscleTotals[muscle] ?: 0.0) + SECONDARY_SET_CREDIT
            }
        }
        val averaged = muscleTotals.map { (muscle, total) ->
            PatternSecondaryMuscleCredit(muscle, total / count)
        }
        if (averaged.isNotEmpty()) {
            result[pattern] = averaged
        }
    }

    // Merge with static fallbacks: patterns not covered by the catalog keep the idealized estimate.
    val merged = PATTERN_SECONDARY_MUSCLE_CREDITS.toMutableMap()
    merged.putAll(result)
    return merged
}

/** Minimum direct (primary) sets for a specialized muscle's isolation slot in one session. */
const val MIN_SPECIALIZED_DIRECT_SETS = 2

/** Minimum direct sets for a cross-domain touch isolation slot (supplementary volume). */
const val MIN_TOUCH_DIRECT_SETS = 2
