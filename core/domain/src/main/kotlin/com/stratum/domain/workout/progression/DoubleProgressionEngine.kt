package com.stratum.domain.workout.progression

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.incrementFor
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotRecommendation
import javax.inject.Inject

/**
 * Double-progression engine (D8 primary). Increments load when the TOP of the rep
 * range was achieved at the RIR target in history; holds load otherwise. Backoff
 * slots inherit the top-set weight (AMRAP-at-RIR). Pure.
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
 * history). `history` is used only for `previousWeight`/`previousReps` (UI display).
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
        // TOP of range at RIR: reps >= repMax AND rir >= rirGoal.
        return set.reps >= slot.repMax && (set.rir != null && set.rir >= slot.rirGoal)
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
