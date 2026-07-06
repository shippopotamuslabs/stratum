package com.stratum.domain.workout

import com.stratum.domain.importing.EstimatedOneRm
import com.stratum.domain.lift.Weight
import javax.inject.Inject

/**
 * Overlay-only recommendation engine (D4). Hold-vs-reduce decision for the next
 * upcoming set, computed from the most recent performed set.
 *
 * - Top set (has rep range): targets `slot.repMin`.
 * - Backoff set (RIR-only): targets [BACKOFF_REP_FLOOR].
 *
 * Projects achievable reps at `currentWeight` (inverse forward formula minus the
 * target RIR). HOLD if projected ≥ target; REDUCE to `weightForReps(e1rm, target)`
 * otherwise. `lastPerformed` is nullable: returns null when no performed set exists
 * yet for the lift (the upcoming set is the first set of the session — no e1RM can
 * be derived). Never writes back into the snapshot.
 */
class RecommendationEngine @Inject constructor() {
    fun recommendNextSet(
        lastPerformed: LoggedSetValue?,
        completedSlot: SetSlot?,
        targetSlot: SetSlot,
        currentWeight: Weight,
    ): RecommendationOutcome? {
        if (lastPerformed == null || lastPerformed.weight.amount <= 0.0) return null

        // If next set is blank (currentWeight <= 0.0), use current set's weight as fallback
        val effectiveWeight = if (currentWeight.amount <= 0.0) lastPerformed.weight else currentWeight

        val effectiveRir = lastPerformed.rir ?: 0
        val reps = lastPerformed.reps + effectiveRir
        val e1rm = if (reps > 0) EstimatedOneRm.calculate(lastPerformed.weight.amount, reps) else 0.0
        val targetReps = if (targetSlot.isTopSet) targetSlot.repMin else BACKOFF_REP_FLOOR

        if (e1rm <= 0.0 || targetReps <= 0) return null

        val projectedReps = EstimatedOneRm.repsForWeight(e1rm, effectiveWeight.amount) - targetSlot.rirGoal

        // Check if the first set was "too easy" to bump up the weight of next set(s).
        // Too easy threshold: top of the rep range (repMax), normalized for RIR, is exceeded by >= 2 reps.
        val achievedRepsAtTargetRir = lastPerformed.reps + effectiveRir - (completedSlot?.rirGoal ?: 0)
        val isTooEasy = completedSlot != null && completedSlot.isTopSet &&
                (achievedRepsAtTargetRir - completedSlot.repMax >= 2)

        return if (isTooEasy) {
            // Bump up weight of next set(s) by 5 lbs
            val bumpedWeight = Weight(effectiveWeight.amount + 5.0, effectiveWeight.unit)
            RecommendationOutcome.Hold(bumpedWeight)
        } else if (projectedReps >= targetReps) {
            RecommendationOutcome.Hold(effectiveWeight)
        } else {
            val reduced = EstimatedOneRm.weightForReps(e1rm, targetReps)
            RecommendationOutcome.Reduce(Weight(reduced, effectiveWeight.unit))
        }
    }
}
