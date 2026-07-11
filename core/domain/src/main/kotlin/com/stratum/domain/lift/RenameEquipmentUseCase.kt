package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/** Renames an equipment row. Seed and custom equipment may both be renamed by the user. */
class RenameEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: EquipmentId, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Equipment name is required"))
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to rename equipment") {
            repository.renameEquipment(id, trimmed)
        }
    }

    private companion object {
        const val TAG = "RenameEquipment"
    }
}
