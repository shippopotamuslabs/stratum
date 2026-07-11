package com.stratum.domain.workout

import com.stratum.domain.lift.Weight

/**
 * Global floor for weighted progression **recommended reps** (D4). Applies to top sets and
 * backoff/RIR-only slots: engines and the live overlay pipeline never recommend fewer than
 * this many reps. When capacity at the current load would fall below the floor, prefer
 * dropping weight (via increment tiers) so the lifter can hit ≥[BACKOFF_REP_FLOOR] rather
 * than prescribing a sub-floor rep count. Tunable.
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
