package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Assigns or clears the equipment reference on a lift (catalog or custom). Changing equipment
 * also clears lift-scoped increment override tiers so effective increments re-resolve from the
 * new equipment defaults (or global fallback when unassigned).
 */
class SetLiftEquipmentUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LiftId, equipmentId: EquipmentId?): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set lift equipment") {
            repository.setLiftEquipment(id, equipmentId)
        }

    private companion object {
        const val TAG = "SetLiftEquipment"
    }
}
