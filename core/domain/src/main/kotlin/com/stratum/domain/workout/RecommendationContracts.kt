package com.stratum.domain.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.workout.fatigue.FatigueModel

/**
 * Global floor for weighted progression **recommended reps** (D4). Applies to top sets and
 * backoff/RIR-only slots: engines and the live overlay pipeline never recommend fewer than
 * this many reps. When capacity at the current load would fall below the floor, prefer
 * dropping weight (via increment tiers) so the lifter can hit ≥[BACKOFF_REP_FLOOR] rather
 * than prescribing a sub-floor rep count. Tunable.
 */
const val BACKOFF_REP_FLOOR: Int = 5

/**
 * Overlay-only weight decision (D4). Production consumers typically only need the [Weight];
 * the branch is retained for tests and telemetry.
 *
 * - [Hold]: use this load (prescribed, re-anchored, or a successful too-easy bump).
 * - [Reduce]: use this lighter load so the last remaining set clears [BACKOFF_REP_FLOOR].
 * - Engine `null`: keep snapshot **weight** (on-track straight set, bodyweight/assisted,
 *   or no e1RM). [RecomputeRecommendationsUseCase] may still emit live **reps** at that weight.
 */
sealed interface RecommendationOutcome {
    data class Hold(val weight: Weight) : RecommendationOutcome
    data class Reduce(val weight: Weight) : RecommendationOutcome
}

/**
 * Overlay-only live recommendation for an upcoming unlogged set (D4). Weight and reps are
 * derived from one [com.stratum.domain.workout.fatigue.FatigueModel] capacity story
 * ([RecommendationEngine] + [RecomputeRecommendationsUseCase]). Stored in
 * `WorkoutOverlay.recommendations` and persisted with the session overlay so minimize/reopen
 * can restore mid-block freeze; never written back into the progression snapshot. On-track
 * performances keep snapshot weight but still receive fatigue-projected reps.
 */
data class LiveSetRecommendation(
    val weight: Weight,
    val reps: Int,
)
