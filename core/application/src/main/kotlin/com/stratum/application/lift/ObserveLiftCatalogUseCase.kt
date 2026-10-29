package com.stratum.application.lift

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.stratum.domain.lift.Lift

/**
 * Reads the lift catalog stream (every lift with its current `enabled` flag). The single
 * entry point ViewModels use to observe the catalog — they never call [LiftRepository]
 * directly (ARCHITECTURE.md §4.4).
 */
class ObserveLiftCatalogUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    operator fun invoke(): Flow<List<Lift>> = repository.observeLifts()
}
