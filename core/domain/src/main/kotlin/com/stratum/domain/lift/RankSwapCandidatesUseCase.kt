package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A catalog lift paired with its [liftSimilarity] score against the swap target. Higher
 * [similarity] means closer biomechanical match; the swap sheet renders options in descending
 * order so the best matches surface first without a search.
 */
data class ScoredLift(
    val lift: Lift,
    val similarity: Double,
)

/**
 * Ranks the user's *enabled* catalog by biomechanical similarity to a target lift — the
 * deterministic engine behind the swap sheet's "smart display" (AGENTS.md §4.6). The target
 * itself is dropped from the result; disabled lifts never appear.
 *
 * Hot stream: re-emits whenever the catalog changes (lift enabled/disabled, custom lift added).
 * ViewModels collect this and overlay filter/search state to produce the final sheet state
 * (ARCHITECTURE.md §4.4 — only transient state lives in the ViewModel).
 *
 * If the target id is not in the enabled catalog, the stream still emits — every enabled lift
 * is scored against `null`, so all candidates land at the same baseline and sort by `mainstream`
 * then name. Callers typically open the sheet only with a known-good target id.
 */
class RankSwapCandidatesUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    operator fun invoke(targetLiftId: LiftId): Flow<List<ScoredLift>> =
        repository.observeEnabledLifts().map { enabled ->
            val target = enabled.firstOrNull { it.id == targetLiftId }
            enabled
                .asSequence()
                .filter { it.id != targetLiftId }
                .map { lift ->
                    ScoredLift(
                        lift = lift,
                        similarity = if (target != null) liftSimilarity(target, lift) else 0.0,
                    )
                }
                .sortedWith(
                    compareByDescending<ScoredLift> { it.similarity }
                        .thenByDescending { it.lift.mainstream }
                        .thenBy { it.lift.name.lowercase() },
                )
                .toList()
        }
}
