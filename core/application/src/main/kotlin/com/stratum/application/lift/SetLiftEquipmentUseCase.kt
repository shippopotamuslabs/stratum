package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.alignedWithEquipment

/**
 * Outcome of assigning equipment to a lift (STR-243).
 *
 * [EquipmentDeleted] is the tombstone refusal (STR-105 Scope 8): a deleted
 * equipment can never be (re-)persisted as an assignment. The originating
 * throwable of a failed read or write goes once to the diagnostics channel
 * ([LoggingPort.error]) at the use-case boundary; it never crosses as a
 * payload. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface SetLiftEquipmentResult {
    data object Success : SetLiftEquipmentResult

    data class LiftNotFound(val liftId: LiftId) : SetLiftEquipmentResult

    data class EquipmentDeleted(val equipmentId: EquipmentId) : SetLiftEquipmentResult

    /** A catalog read or the assignment write failed (repository / store I/O). */
    data object PersistenceFailed : SetLiftEquipmentResult
}

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
    suspend operator fun invoke(id: LiftId, equipmentId: EquipmentId?): SetLiftEquipmentResult =
        try {
            val lift =
                repository.getLiftsByIds(listOf(id)).firstOrNull()
                    ?: return SetLiftEquipmentResult.LiftNotFound(id)
            // STR-105 Scope 8: a tombstoned assignment must never be (re-)persisted.
            // The caller must pick a live equipment or clear the assignment (null).
            if (equipmentId != null &&
                equipmentRepository.getEquipment(equipmentId)?.isTombstoned == true
            ) {
                return SetLiftEquipmentResult.EquipmentDeleted(equipmentId)
            }
            val nextLoad = lift.loadType.alignedWithEquipment(equipmentId)
            repository.setLiftEquipment(id, equipmentId, nextLoad)
            SetLiftEquipmentResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set lift equipment: ${t.message}", t)
            SetLiftEquipmentResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetLiftEquipment"
    }
}
