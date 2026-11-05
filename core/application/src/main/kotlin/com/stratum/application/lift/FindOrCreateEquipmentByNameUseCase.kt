package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import com.stratum.domain.lift.CatalogNames

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
        // STR-243 C3 minimal caller touch (this file converts in C4): the name rule
        // now returns a typed validation; map Invalid onto the same Result failure.
        val trimmed =
            when (val nameResult = CatalogNames.validateEquipmentName(name)) {
                is CatalogNames.Validation.Valid -> nameResult.name
                is CatalogNames.Validation.Invalid ->
                    return Result.failure(IllegalArgumentException(nameResult.message))
            }
        return runCatchingLogged(loggingPort, TAG, "Failed to find or create equipment") {
            repository.findOrCreateByName(trimmed)
        }
    }

    private companion object {
        const val TAG = "FindOrCreateEquipment"
    }
}
