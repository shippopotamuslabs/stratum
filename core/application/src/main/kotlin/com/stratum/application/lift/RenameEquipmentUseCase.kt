package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import com.stratum.domain.lift.CatalogNames

/** Renames an equipment row. Seed and custom equipment may both be renamed by the user. */
class RenameEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: EquipmentId, name: String): Result<Unit> {
        val trimmed = CatalogNames.equipmentNameResult(name).getOrElse { return Result.failure(it) }
        return runCatchingLogged(loggingPort, TAG, "Failed to rename equipment") {
            repository.renameEquipment(id, trimmed)
        }
    }

    private companion object {
        const val TAG = "RenameEquipment"
    }
}
