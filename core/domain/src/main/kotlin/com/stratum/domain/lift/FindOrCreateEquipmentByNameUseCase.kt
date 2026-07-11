package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Finds equipment for a free-text label (exact / normalized / seed alias) or creates a custom
 * row with default single-band increments. Used when free-text equipment is entered on
 * create/import paths.
 */
class FindOrCreateEquipmentByNameUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(name: String): Result<EquipmentId> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Equipment name is required"))
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to find or create equipment") {
            repository.findOrCreateByName(trimmed)
        }
    }

    private companion object {
        const val TAG = "FindOrCreateEquipment"
    }
}
