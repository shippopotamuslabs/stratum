package com.stratum.domain.workout.progression

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.incrementFor
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SlotRecommendation
import javax.inject.Inject
import com.stratum.domain.workout.SlotHistoryEntry

/**
 * Double-progression engine (D8 primary). Increments load when failure capacity meets
 * the top-of-range target in history:
 * `(reps + rir) >= (repMax + rirGoal)`
 * — i.e. completed capacity at-RIR is at least the goal capacity for top-of-range at
 * the RIR target. Holds load otherwise. Backoff slots inherit the top-set weight
 * (AMRAP-at-RIR). Pure.
 *
 * Load selection only lives here; shared recommend orchestration
 * ([recommendShared]) owns floor drops, fatigue projection, inherit guards, and
 * cold-start rep floors.
 *
 * Increment unit default is 5 lb; production wiring can supply a per-lift override
 * through the eventual config (D8). Math baseline comes from
 * [SlotProgressionRequest.progressionBaseline] — the orchestrator builds a
 * capacity-ordered recent-session block per lift, so recommendations are monotonically
 * non-increasing across slots even when prior-set RIR was misreported ("reps going up"
 * history). `history` is used only for `previousWeight`/`previousReps`/`previousRir` (UI display).
 */
class DoubleProgressionEngine @Inject constructor() : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Double

    override fun recommend(request: SlotProgressionRequest): SlotRecommendation =
        recommendShared(
            request = request,
            policy = ProgressionLoadPolicy(
                shouldAdvanceLoad = ::shouldIncrement,
                incrementFor = { req, base -> incrementFor(req, base) },
                heldTopRepsWithoutRir = { prevReps, _ -> prevReps },
            ),
        )

    private fun shouldIncrement(
        latest: SlotHistoryEntry?,
        slot: SetSlot,
    ): Boolean {
        val set = latest?.loggedSet ?: return false
        val rir = set.rir ?: return false
        // Goal capacity (top of range at RIR target) <= completed capacity (reps + rir).
        return set.reps + rir >= slot.repMax + slot.rirGoal
    }

    private fun incrementFor(request: SlotProgressionRequest, baseWeight: Weight): Weight {
        val fromTiers = request.incrementTiers.incrementFor(baseWeight)
        if (fromTiers != null) return fromTiers
        return Weight(DEFAULT_DOUBLE_INCREMENT_LB, baseWeight.unit)
    }

    private companion object {
        const val DEFAULT_DOUBLE_INCREMENT_LB = 5.0
    }
}
