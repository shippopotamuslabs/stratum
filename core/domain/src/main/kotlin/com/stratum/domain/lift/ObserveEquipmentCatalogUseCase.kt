package com.stratum.domain.lift

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Observes the full equipment catalog (seed + custom) with default increment tiers. */
class ObserveEquipmentCatalogUseCase @Inject constructor(
    private val repository: EquipmentRepository,
) {
    operator fun invoke(): Flow<List<Equipment>> = repository.observeEquipment()
}
