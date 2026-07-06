package com.stratum.domain.workout

import com.stratum.domain.lift.Weight

/**
 * "Meaningful backoff set" floor (D4). At least [BACKOFF_REP_FLOOR] reps at the target
 * RIR. Lifts the "reasonable rep count by the last backoff set" intent into a named,
 * testable constant. Tunable.
 */
const val BACKOFF_REP_FLOOR: Int = 5

/**
 * Overlay-only recommendation outcome (D4). Hold-vs-reduce decision for the next
 * upcoming set, computed from the most recent performed set.
 */
sealed interface RecommendationOutcome {
    data class Hold(val weight: Weight) : RecommendationOutcome
    data class Reduce(val weight: Weight) : RecommendationOutcome
}
