package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Tombstones equipment (deleted flag; row retained for history). Every visible
 * seed row and every custom row is deletable; the hidden system rows
 * ([SeedEquipmentIds.hiddenFromCatalog]) can never be deleted. Live pointers
 * are reassigned to [replacementId] atomically with the tombstone (Bodyweight
 * fallback when no candidate exists); logged history is never rewritten.
 * Returns failure when the row is protected, missing, or the replacement
 * contract is violated (see [EquipmentRepository.deleteCustomEquipment]).
 */
class DeleteCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: EquipmentId, replacementId: EquipmentId? = null): Result<Unit> {
        val deleted =
            runCatchingLogged(loggingPort, TAG, "Failed to delete custom equipment") {
                repository.deleteCustomEquipment(id, replacementId)
            }.getOrElse { return Result.failure(it) }

        return if (deleted) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "Equipment cannot be deleted (protected or missing, or a replacement is required)",
                ),
            )
        }
    }

    private companion object {
        const val TAG = "DeleteCustomEquipment"
    }
}
