package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.SeedEquipmentIds

/**
 * Outcome of deleting custom equipment (STR-243; STR-237 Route (i)).
 *
 * [NotDeletable] is the repository's refusal: the row is protected or missing,
 * or the replacement contract was violated. The originating throwable of a
 * failed write rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface DeleteCustomEquipmentResult {
    data object Success : DeleteCustomEquipmentResult

    data class NotDeletable(val equipmentId: EquipmentId) : DeleteCustomEquipmentResult

    /** The equipment write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : DeleteCustomEquipmentResult
}

/**
 * Tombstones equipment (deleted flag; row retained for history). Every visible
 * seed row and every custom row is deletable; the hidden system rows
 * ([SeedEquipmentIds.hiddenFromCatalog]) can never be deleted. Live pointers
 * are reassigned to [replacementId] atomically with the tombstone (Bodyweight
 * fallback when no candidate exists); logged history is never rewritten.
 * Returns [DeleteCustomEquipmentResult.NotDeletable] when the row is
 * protected, missing, or the replacement contract is violated (see
 * [EquipmentRepository.deleteCustomEquipment]).
 */
class DeleteCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
) {
    suspend operator fun invoke(
        id: EquipmentId,
        replacementId: EquipmentId? = null,
    ): DeleteCustomEquipmentResult =
        try {
            if (repository.deleteCustomEquipment(id, replacementId)) {
                DeleteCustomEquipmentResult.Success
            } else {
                DeleteCustomEquipmentResult.NotDeletable(id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            DeleteCustomEquipmentResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to delete custom equipment: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "DeleteCustomEquipment"
    }
}
