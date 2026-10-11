package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Tombstones custom equipment (deleted flag; row retained for history). Seed equipment
 * cannot be deleted. Referenced lifts keep resolving the tombstoned row — no
 * reassignment. Returns failure when the row is seed equipment or missing.
 */
class DeleteCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: EquipmentId): Result<Unit> {
        val deleted =
            runCatchingLogged(loggingPort, TAG, "Failed to delete custom equipment") {
                repository.deleteCustomEquipment(id)
            }.getOrElse { return Result.failure(it) }

        return if (deleted) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException("Equipment cannot be deleted (seed equipment or missing)"),
            )
        }
    }

    private companion object {
        const val TAG = "DeleteCustomEquipment"
    }
}
