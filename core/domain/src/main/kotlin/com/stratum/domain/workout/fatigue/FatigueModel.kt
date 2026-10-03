package com.stratum.domain.workout.fatigue

import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.lift.Weight
import com.stratum.domain.response.POPULATION_FATIGUE_DECAY_INTERCEPT
import com.stratum.domain.response.POPULATION_FATIGUE_DECAY_SLOPE
import kotlin.math.roundToInt

/**
 * Fatigue-model port (D5/D7, Q2). Projects expected e1RM (and thus expected reps at
 * a given weight) one set-index forward from the last performed/top-set e1RM. Per-lift
 * values override the population default when present.
 */
interface FatigueModel {
    fun expectedRepsForAddedSet(
        baselineOneRm: Double,
        targetWeight: Weight,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int
}

/**
 * Projects expected reps for an added set via a linear-decay model: reps decline by
 * `slope` per set index (intercept offsets the baseline). Per-lift slope/intercept
 * override the [POPULATION_FATIGUE_DECAY_SLOPE]/[POPULATION_FATIGUE_DECAY_INTERCEPT]
 * defaults when present (Q2 resolution). Reps are monotonically non-increasing in
 * set index for any non-positive slope.
 */
fun projectReps(
    baselineOneRm: Double,
    targetWeight: Weight,
    nextSetIndex: Int,
    perLiftSlope: Double?,
    perLiftIntercept: Double?,
): Int {
    if (baselineOneRm <= 0.0 || targetWeight.amount <= 0.0) return 1
    val baselineReps = EstimatedOneRm.repsForWeight(baselineOneRm, targetWeight.amount)
    return applyFatigueDecay(
        baselineReps = baselineReps,
        setIndex = nextSetIndex,
        perLiftSlope = perLiftSlope,
        perLiftIntercept = perLiftIntercept,
    )
}

/**
 * Shared linear-decay core for rep projections: resolves slope/intercept (per-lift
 * values override the population defaults when present), applies
 * `slope * (setIndex - 1) + intercept` to [baselineReps], floors at [minReps], and
 * rounds to whole reps. Slope and intercept are clamped non-positive so decay never
 * increases reps above the baseline.
 */
internal fun applyFatigueDecay(
    baselineReps: Double,
    setIndex: Int,
    perLiftSlope: Double?,
    perLiftIntercept: Double?,
    minReps: Double = 1.0,
): Int {
    val slope = (perLiftSlope ?: POPULATION_FATIGUE_DECAY_SLOPE).coerceAtMost(0.0)
    val intercept = (perLiftIntercept ?: POPULATION_FATIGUE_DECAY_INTERCEPT).coerceAtMost(0.0)
    val decay = slope * (setIndex - 1) + intercept
    val projected = baselineReps + decay.coerceAtMost(0.0)
    return projected.coerceAtLeast(minReps).roundToInt()
}

/** Default population-gradient implementation (Q2). */
class PopulationFatigueModel : FatigueModel {
    override fun expectedRepsForAddedSet(
        baselineOneRm: Double,
        targetWeight: Weight,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int = projectReps(baselineOneRm, targetWeight, nextSetIndex, perLiftSlope, perLiftIntercept)
}
