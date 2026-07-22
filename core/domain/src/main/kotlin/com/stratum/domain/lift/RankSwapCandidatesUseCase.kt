package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

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
 * itself is dropped from the result; disabled candidates never appear.
 *
 * The **target** may itself be recommendation-disabled. Similarity still uses its catalog
 * metadata so the user can swap a non-recommended lift for a close enabled match. Only
 * candidate rows require `enabled = true`.
 *
 * Hot stream: re-emits whenever the **enabled** catalog changes. Target metadata is resolved
 * from the enabled stream when present, otherwise via a bounded [LiftRepository.getLiftsByIds]
 * lookup (DATA_LAYER_RULES.md §5 — avoid loading the full catalog only to drop disabled rows).
 *
 * If the target id is not in the catalog at all, the stream still emits — every enabled lift
 * is scored against `null`, so all candidates land at the same baseline and sort by `mainstream`
 * then name. Callers typically open the sheet only with a known-good target id.
 */
class RankSwapCandidatesUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(targetLiftId: LiftId): Flow<List<ScoredLift>> =
        repository.observeEnabledLifts().mapLatest { enabled ->
            val target =
                enabled.firstOrNull { it.id == targetLiftId }
                    ?: repository.getLiftsByIds(listOf(targetLiftId)).firstOrNull()
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
