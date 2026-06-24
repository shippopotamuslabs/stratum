package com.stratum.domain.lift

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

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
