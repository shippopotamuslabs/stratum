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
        targetSlot: SetSlot,
        currentWeight: Weight,
    ): RecommendationOutcome? {
        if (lastPerformed == null) return null

        val effectiveRir = lastPerformed.rir ?: 0
        val e1rm = EstimatedOneRm.calculate(lastPerformed.weight.amount, lastPerformed.reps + effectiveRir)
        val targetReps = if (targetSlot.isTopSet) targetSlot.repMin else BACKOFF_REP_FLOOR
        val projectedReps = EstimatedOneRm.repsForWeight(e1rm, currentWeight.amount) - targetSlot.rirGoal

        return if (projectedReps >= targetReps) {
            RecommendationOutcome.Hold(currentWeight)
        } else {
            val reduced = EstimatedOneRm.weightForReps(e1rm, targetReps)
            RecommendationOutcome.Reduce(Weight(reduced, currentWeight.unit))
        }
    }
}
