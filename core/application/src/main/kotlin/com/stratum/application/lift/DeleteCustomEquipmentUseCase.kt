package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.SeedEquipmentIds

/**
 * Outcome of deleting custom equipment (STR-243).
 *
 * [NotDeletable] is the repository's refusal: the row is protected or missing,
 * or the replacement contract was violated. The originating throwable of a
 * failed write goes once to the diagnostics channel ([LoggingPort.error]) at
 * the use-case boundary; it never crosses as a payload. Coroutine cancellation
 * is not a branch: it propagates.
 */
sealed interface DeleteCustomEquipmentResult {
    data object Success : DeleteCustomEquipmentResult

    data class NotDeletable(val equipmentId: EquipmentId) : DeleteCustomEquipmentResult

    /** The equipment write failed (repository / store I/O). */
    data object PersistenceFailed : DeleteCustomEquipmentResult
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
    private val loggingPort: LoggingPort,
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
            loggingPort.error(TAG, "Failed to delete custom equipment: ${t.message}", t)
            DeleteCustomEquipmentResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "DeleteCustomEquipment"
    }
}
