package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Deletes custom equipment that is not referenced by any lift. Seed equipment cannot be
 * deleted. Returns failure when the row is still in use or is not custom.
 */
class DeleteCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: EquipmentId): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to delete custom equipment") {
            val deleted = repository.deleteCustomEquipment(id)
            if (!deleted) {
                error("Equipment cannot be deleted (seed, missing, or still in use)")
            }
        }

    private companion object {
        const val TAG = "DeleteCustomEquipment"
    }
}
