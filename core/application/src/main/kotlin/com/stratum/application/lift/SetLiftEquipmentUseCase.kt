package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.alignedWithEquipment

/**
 * Outcome of assigning equipment to a lift (STR-243; STR-237 Route (i)).
 *
 * [EquipmentDeleted] is the tombstone refusal (STR-105 Scope 8): a deleted
 * equipment can never be (re-)persisted as an assignment. The originating
 * throwable of a failed read or write rides the [PersistenceFailed] branch
 * as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); the presentation boundary drains it exactly once
 * through its module's DiagnosticDrain. Coroutine cancellation is not a
 * branch: it propagates.
 */
sealed interface SetLiftEquipmentResult {
    data object Success : SetLiftEquipmentResult

    data class LiftNotFound(val liftId: LiftId) : SetLiftEquipmentResult

    data class EquipmentDeleted(val equipmentId: EquipmentId) : SetLiftEquipmentResult

    /** A catalog read or the assignment write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : SetLiftEquipmentResult
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
            SetLiftEquipmentResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set lift equipment: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetLiftEquipment"
    }
}
