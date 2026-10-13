package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Assigns or clears the equipment reference on a lift (catalog or custom). Changing equipment
 * also clears lift-scoped increment override tiers so effective increments re-resolve from the
 * new equipment defaults (or global fallback when unassigned), and couples [LoadType] via
 * [alignedWithEquipment] in the same write.
 */
class SetLiftEquipmentUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val equipmentRepository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LiftId, equipmentId: EquipmentId?): Result<Unit> {
        val lift =
            repository.getLiftsByIds(listOf(id)).firstOrNull()
                ?: return Result.failure(IllegalArgumentException("Lift not found"))
        // STR-105 Scope 8: a tombstoned assignment must never be (re-)persisted.
        // The caller must pick a live equipment or clear the assignment (null).
        if (equipmentId != null && equipmentRepository.getEquipment(equipmentId)?.isDeleted == true) {
            return Result.failure(
                IllegalStateException("Equipment is deleted; pick a live equipment or clear the assignment"),
            )
        }
        val nextLoad = lift.loadType.alignedWithEquipment(equipmentId)
        return runCatchingLogged(loggingPort, TAG, "Failed to set lift equipment") {
            repository.setLiftEquipment(id, equipmentId, nextLoad)
        }
    }

    private companion object {
        const val TAG = "SetLiftEquipment"
    }
}
