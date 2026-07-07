package com.stratum.domain.workout

import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.response.POPULATION_FATIGUE_DECAY_SLOPE
import javax.inject.Inject

/**
 * Overlay-only recommendation engine (D4). Hold-vs-reduce decision for the next upcoming
 * set, computed from the most recent performed set.
 *
 * - Top set (has rep range): targets `slot.repMin`.
 * - Backoff set (RIR-only): targets [BACKOFF_REP_FLOOR].
 *
 * Decision tree (in order):
 * 1. **Too hard** — the lifter missed the rep-range floor (or backoff floor) by ≥ 2 reps
 *    considering RIR. REDUCE to a weight where the **last** remaining set hits the target
 *    reps at the target RIR, accounting for fatigue decay across remaining sets.
 * 2. **Too easy** — top-set ceiling exceeded by ≥ 2 reps considering RIR. Bump +5 lb and
 *    round to the lift's increment tier. (The e1RM-based bump round-trips back to the
 *    current weight when the user exceeded the target, so a flat increment is used.)
 * 3. **Hold vs Reduce** — if projected reps at the current weight ≥ target reps, HOLD.
 *    Otherwise REDUCE to the last-set-targeted weight (same formula as too-hard).
 *
 * The reduce weight targets the **last** remaining set (most-fatigued) so every set stays
 * in range: `weightForReps(e1rm, targetReps + rirGoal + fatigueForLastSet)` where
 * `fatigueForLastSet = |POPULATION_FATIGUE_DECAY_SLOPE| × (remainingCount − 1)`.
 *
 * Output weights are rounded to the lift's [incrementTiers] so suggestions land on
 * realistic load jumps. `lastPerformed` is nullable: returns null when no performed set
 * exists yet for the lift (the upcoming set is the first set — no e1RM can be derived).
 * Never writes back into the snapshot.
 *
 * [loadType] (default [LoadType.Weighted]) shapes the math:
 * - **Bodyweight** ([LoadType.Bodyweight]): there is no load lever — returns `null` so the
 *   UI keeps the 0-weight placeholder. Rep projection is handled by [RecomputeRecommendationsUseCase].
 * - **Assisted** ([LoadType.Assisted]): the e1RM formula assumes weight = positive load, but
 *   assisted weights are negative, so the e1RM goes negative and trips the `> 0` guard. For
 *   v1 the live engine defers to the snapshot baseline (returns `null`); the snapshot engine
 *   already handles assisted progression correctly (increment toward 0, cap at 0). A future
 *   abs-based e1RM reformulation could enable intra-session assisted recommendations.
 * - **Weighted** ([LoadType.Weighted]): unchanged (current behavior).
 */
class RecommendationEngine @Inject constructor() {
    fun recommendNextSet(
        lastPerformed: LoggedSetValue?,
        completedSlot: SetSlot?,
        targetSlot: SetSlot,
        currentWeight: Weight,
        remainingCount: Int = 1,
        incrementTiers: List<IncrementTier> = emptyList(),
        perLiftSlope: Double? = null,
        loadType: LoadType = LoadType.Weighted,
    ): RecommendationOutcome? {
        // Bodyweight: no load lever — rep-only progression (rep projection upstream).
        if (loadType == LoadType.Bodyweight) return null
        // Assisted: the e1RM formula assumes positive load, but assisted weights are
        // negative, so the e1RM goes negative and trips the > 0 guard. For v1 the live
        // engine defers to the snapshot baseline (which handles assisted progression
        // correctly); a future abs-based e1RM reformulation could enable this path.
        if (loadType == LoadType.Assisted) return null
        if (lastPerformed == null || lastPerformed.weight.amount <= 0.0) return null

        // If next set is blank (currentWeight <= 0.0), use current set's weight as fallback
        val effectiveWeight = if (currentWeight.amount <= 0.0) {
            lastPerformed.weight
        } else {
            currentWeight
        }

        val effectiveRir = lastPerformed.rir ?: 0
        val reps = lastPerformed.reps + effectiveRir
        val e1rm = if (reps > 0) EstimatedOneRm.calculate(lastPerformed.weight.amount, reps) else 0.0
        val targetReps = if (targetSlot.isTopSet) targetSlot.repMin else BACKOFF_REP_FLOOR

        if (e1rm <= 0.0 || targetReps <= 0) return null

        // Fatigue decay (0-RIR reps) from the next set to the last remaining set. With
        // remainingCount = 1, fatigueForLastSet = 0 (last set == next set). Per-lift slope
        // overrides the population default when present, so the weight-reduction math stays
        // consistent with the rep-projection math (both use the same slope).
        val fatigueSlope = perLiftSlope ?: POPULATION_FATIGUE_DECAY_SLOPE
        val fatigueForLastSet = (fatigueSlope * (remainingCount - 1)).toInt().unaryMinus()

        // achievedRepsAtTargetRir normalizes the performed reps to the completed slot's RIR
        // intent so the floor/ceiling comparisons are apples-to-apples.
        val achievedRepsAtTargetRir = lastPerformed.reps + effectiveRir - (completedSlot?.rirGoal ?: 0)
        val targetFloor = if (completedSlot?.isTopSet == true) completedSlot.repMin else BACKOFF_REP_FLOOR

        // Too hard: missed the floor by ≥ 2 reps considering RIR. The e1RM round-trip can
        // mask this (e.g. 75×2@2 projects 4 @ 2 RIR), so an explicit trigger is needed.
        val isTooHard = completedSlot != null && (targetFloor - achievedRepsAtTargetRir) >= 2

        // Too easy: exceeded the top-set ceiling by ≥ 2 reps considering RIR.
        val isTooEasy = completedSlot != null && completedSlot.isTopSet &&
            (achievedRepsAtTargetRir - completedSlot.repMax >= 2)

        return if (isTooHard) {
            // Reduce to the weight at which the last remaining set hits targetReps at the
            // slot's RIR goal. `weightForReps` is defined at 0 RIR, so the target reps are
            // shifted by `rirGoal` plus `fatigueForLastSet` (0-RIR rep decay to the last set).
            val reduced = EstimatedOneRm.weightForReps(e1rm, targetReps + targetSlot.rirGoal + fatigueForLastSet)
            RecommendationOutcome.Reduce(
                incrementTiers.roundToIncrement(Weight(reduced, effectiveWeight.unit), loadType),
            )
        } else if (isTooEasy) {
            // Bump up weight by a flat 5 lb (the e1RM-based bump round-trips back when the
            // user exceeded the target, so a fixed increment is the reliable signal).
            val bumpedWeight = Weight(effectiveWeight.amount + 5.0, effectiveWeight.unit)
            RecommendationOutcome.Hold(incrementTiers.roundToIncrement(bumpedWeight, loadType))
        } else {
            val projectedReps = EstimatedOneRm.repsForWeight(e1rm, effectiveWeight.amount) - targetSlot.rirGoal
            if (projectedReps >= targetReps) {
                // currentWeight is already tier-aligned from the snapshot — no rounding needed.
                RecommendationOutcome.Hold(effectiveWeight)
            } else {
                val reduced = EstimatedOneRm.weightForReps(e1rm, targetReps + targetSlot.rirGoal + fatigueForLastSet)
                RecommendationOutcome.Reduce(
                    incrementTiers.roundToIncrement(Weight(reduced, effectiveWeight.unit), loadType),
                )
            }
        }
    }
}
