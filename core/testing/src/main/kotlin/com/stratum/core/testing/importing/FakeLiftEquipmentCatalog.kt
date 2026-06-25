package com.stratum.core.testing.importing

import com.stratum.domain.importing.GymEquipment
import com.stratum.domain.importing.LiftEquipmentCatalogPort
import com.stratum.domain.lift.identity.LiftId

/**
 * In-memory [LiftEquipmentCatalogPort] for domain tests. Defaults to empty (no equipment closure);
 * tests seed it via [equipmentByLiftId].
 */
class FakeLiftEquipmentCatalog(
    private val equipmentByLiftId: Map<LiftId, Set<GymEquipment>> = emptyMap(),
) : LiftEquipmentCatalogPort {
    override fun equipmentFor(liftId: LiftId): Set<GymEquipment> =
        equipmentByLiftId[liftId] ?: emptySet()

    override fun all(): Map<LiftId, Set<GymEquipment>> = equipmentByLiftId
}
