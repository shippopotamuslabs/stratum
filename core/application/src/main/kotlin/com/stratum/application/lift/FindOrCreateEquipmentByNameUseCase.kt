package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.CatalogNames

/**
 * Outcome of finding or creating equipment by name (STR-243).
 *
 * The name branch carries the validation result itself
 * ([CatalogNames.Validation.Invalid]). The originating throwable of a failed
 * write goes once to the diagnostics channel ([LoggingPort.error]) at the
 * use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates.
 */
sealed interface FindOrCreateEquipmentResult {
    data class Success(val equipmentId: EquipmentId) : FindOrCreateEquipmentResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) :
        FindOrCreateEquipmentResult

    /** The equipment read/write failed (repository / store I/O). */
    data object PersistenceFailed : FindOrCreateEquipmentResult
}

/**
 * Finds equipment for a free-text label (exact / normalized / seed alias) or creates a custom
 * row with default single-band increments. Used when free-text equipment is entered on
 * create/import paths.
 */
class FindOrCreateEquipmentByNameUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(name: String): FindOrCreateEquipmentResult {
        val trimmed =
            when (val nameResult = CatalogNames.validateEquipmentName(name)) {
                is CatalogNames.Validation.Valid -> nameResult.name
                is CatalogNames.Validation.Invalid ->
                    return FindOrCreateEquipmentResult.InvalidName(nameResult)
            }
        return try {
            FindOrCreateEquipmentResult.Success(repository.findOrCreateByName(trimmed))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to find or create equipment: ${t.message}", t)
            FindOrCreateEquipmentResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "FindOrCreateEquipment"
    }
}
