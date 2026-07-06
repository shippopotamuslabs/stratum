package com.stratum.core.testing.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.workout.fatigue.FatigueModel
import com.stratum.domain.workout.fatigue.projectReps

/**
 * In-memory fake for [FatigueModel] (UNIT_TESTING.md §6). The fake supports two modes
 * selected by the caller's arguments so tests can drive both add-set rep estimation
 * (Slice 3) and drop-set weight reduction (Slice 6):
 *
 * - **Rep-projection mode** (add-set rep estimation): the default. Selected whenever
 *   `baselineOneRm > targetWeight.amount` (the realistic case — e1RM strictly exceeds
 *   the working weight). The fake delegates to the population [projectReps] helper — a
 *   steeper negative slope yields fewer reps, and reps are monotonically non-increasing
 *   in set index. A null `perLiftSlope` is treated as the population default, so null
 *   and the population slope produce identical reps.
 *
 * - **Load-percent mode** (drop-set weight reduction): selected when the caller passes a
 *   1:1 `baselineOneRm`-to-`targetWeight` baseline (i.e. `baselineOneRm <=
 *   targetWeight.amount`). No real rep projection supplies a 1:1 baseline because e1RM
 *   is always strictly greater than the working weight, so this marker uniquely
 *   identifies the drop-set pathway used by `EstimateDropSetWeightUseCase`. In this
 *   mode the fake returns the **effective load percent** of the preceding weight — the
 *   integer percentage `(1 - reductionFraction) * 100` — so the use case recovers the
 *   configured reduction exactly (`precedingWeight * percent / 100`). The fraction is
 *   the configured per-lift fraction when a `perLiftSlope` is supplied, else the
 *   population fraction.
 */
class FakeFatigueModel(
    private val populationReductionFraction: Double = 0.10,
    private val perLiftReductionFraction: Double? = null,
) : FatigueModel {
    override fun expectedRepsForAddedSet(
        baselineOneRm: Double,
        targetWeight: Weight,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int {
        if (baselineOneRm <= targetWeight.amount) {
            val fraction = if (perLiftSlope != null) {
                perLiftReductionFraction ?: populationReductionFraction
            } else {
                populationReductionFraction
            }
            return ((1.0 - fraction) * 100.0).toInt()
        }
        return projectReps(baselineOneRm, targetWeight, nextSetIndex, perLiftSlope, perLiftIntercept)
    }
}
