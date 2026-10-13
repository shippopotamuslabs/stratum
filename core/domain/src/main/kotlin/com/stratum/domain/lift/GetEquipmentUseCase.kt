package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import javax.inject.Inject

/**
 * By-id equipment lookup that also resolves tombstoned rows (STR-105 Scope 5):
 * retained names/tiers for grandfathered sessions and legacy orphans. Picker
 * and swap lists must keep using the live catalog instead.
 */
class GetEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
) {
    suspend operator fun invoke(id: EquipmentId): Equipment? = repository.getEquipment(id)
}
