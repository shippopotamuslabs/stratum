package com.stratum.domain.workout.dropset

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.training.EstimatedOneRm
import kotlin.math.roundToInt
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.workout.intensifier.RestDefaultForTransition

/**
 * Zero-rest drop-set rep projection.
 *
 * Weight selection is separate ([EstimateDropSetWeightUseCase] — 20% off prior). This
 * projects **reps at that lighter load** under continuous / near-continuous rest
 * ([RestDefaultForTransition.REST_INTO_DROP_SECONDS] = 0).
 *
 * Pipeline:
 * 1. Build e1RM from the immediate prior set (reps + RIR at prior load).
 * 2. Convert to fresh capacity at the drop load (lighter → more theoretical reps).
 * 3. Keep only [ZERO_REST_CAPACITY_FRACTION] of that capacity — residual metabolic /
 *    local fatigue after 0 rest. Population fatigue (−2/set with a +1 index bump) is
 *    calibrated for full inter-set rest and under-penalizes true drop-set continuity.
 *
 * Per-lift normal-rest [com.stratum.domain.importing.FatigueDecay] is not used here.
 */
object DropSetRepProjection {
    /**
     * Fraction of fresh e1RM capacity available at the drop load with ~0s rest after
     * the prior set. ~0.5 maps a typical 100×8–9 near-fail → 80 lb drop into the ~7–8
     * rep band instead of the optimistic ~11–15 from mild set-index bumps alone.
     */
    const val ZERO_REST_CAPACITY_FRACTION: Double = 0.50

    const val MIN_REPS: Int = 1

    /**
     * Failure-rep capacity (RIR 0) at [dropWeight] after zero rest from [priorWeight].
     */
    fun expectedRepsAtRir0(
        priorWeight: Weight,
        priorReps: Int,
        priorRir: Int?,
        dropWeight: Weight,
    ): Int {
        val priorLb = WeightConversion.loadMagnitudeLb(priorWeight)
        val dropLb = WeightConversion.loadMagnitudeLb(dropWeight)
        if (priorLb <= 0.0 || dropLb <= 0.0) return MIN_REPS
        val failureReps = (priorReps + (priorRir ?: 0)).coerceAtLeast(1)
        val e1rm = EstimatedOneRm.calculate(priorLb, failureReps)
        val freshAtDrop = EstimatedOneRm.repsForWeight(e1rm, dropLb).coerceAtLeast(1)
        return (freshAtDrop * ZERO_REST_CAPACITY_FRACTION)
            .roundToInt()
            .coerceAtLeast(MIN_REPS)
    }

    /** Slot recommendation: capacity at 0 RIR minus [rirGoal]. */
    fun expectedReps(
        priorWeight: Weight,
        priorReps: Int,
        priorRir: Int?,
        dropWeight: Weight,
        rirGoal: Int = 0,
    ): Int {
        val atZero = expectedRepsAtRir0(priorWeight, priorReps, priorRir, dropWeight)
        return (atZero - rirGoal.coerceAtLeast(0)).coerceAtLeast(MIN_REPS)
    }
}
