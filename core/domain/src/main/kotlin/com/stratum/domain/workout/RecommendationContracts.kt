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

/**
 * Overlay-only live recommendation for an upcoming unlogged set (D4). Carries both the
 * recommended weight (from [RecommendationEngine]) and the recommended reps (projected
 * one set-index forward from the most recent performed set via the [FatigueModel]). Stored
 * in `WorkoutOverlay.recommendations`; never written back into the snapshot.
 */
data class LiveSetRecommendation(
    val weight: Weight,
    val reps: Int,
)
