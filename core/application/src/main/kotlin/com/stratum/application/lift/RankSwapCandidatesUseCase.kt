package com.stratum.application.lift

import com.stratum.domain.lift.ScoredLift
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import com.stratum.domain.lift.liftSimilarity

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
