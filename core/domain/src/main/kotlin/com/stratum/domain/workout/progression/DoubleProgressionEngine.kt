package com.stratum.domain.workout.progression

import com.stratum.domain.lift.Weight
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.workout.SlotRecommendation
import javax.inject.Inject

/**
 * Double-progression engine (D8 primary). Increments load when the TOP of the rep
 * range was achieved at the RIR target in history; holds load otherwise. Backoff
 * slots inherit the top-set weight (AMRAP-at-RIR). Pure.
 *
 * Increment unit default is 5 lb; production wiring can supply a per-lift override
 * through the eventual config (D8). History is read in historical-priority order
 * (n−1 first); the most-recent entry drives the increment decision.
 */
class DoubleProgressionEngine @Inject constructor() : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Double

    override fun recommend(request: SlotProgressionRequest): SlotRecommendation {
        val slot = request.slot
        val latest = request.history.firstOrNull()
        val baseWeight = latest?.loggedSet?.weight ?: request.fallbackOneRm ?: defaultWeight()
        val baselineExpectedReps = latest?.loggedSet?.reps ?: slot.repMin

        // Backoff slot (RIR-only, no rep range): inherits the top-set weight.
        if (!slot.isTopSet) {
            return SlotRecommendation(slot.slotId, baseWeight, baselineExpectedReps)
        }

        // Top-set slot: increment when TOP of rep range met at RIR target; hold otherwise.
        val increment = if (shouldIncrement(latest, slot)) incrementFor() else Weight(0.0, baseWeight.unit)
        val next = Weight(baseWeight.amount + increment.amount, baseWeight.unit)
        return SlotRecommendation(slot.slotId, next, slot.repMin)
    }

    private fun shouldIncrement(
        latest: com.stratum.domain.workout.SlotHistoryEntry?,
        slot: com.stratum.domain.workout.SetSlot,
    ): Boolean {
        val set = latest?.loggedSet ?: return false
        // TOP of range at RIR: reps >= repMax AND rir >= rirGoal.
        return set.reps >= slot.repMax && (set.rir != null && set.rir >= slot.rirGoal)
    }

    private fun incrementFor(): Weight =
        Weight(DEFAULT_DOUBLE_INCREMENT_LB, Weight.WeightUnit.Lb)

    private fun defaultWeight(): Weight =
        Weight(0.0, Weight.WeightUnit.Lb)

    private companion object {
        const val DEFAULT_DOUBLE_INCREMENT_LB = 5.0
    }
}
