package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.CatalogNames

/**
 * Outcome of renaming equipment (STR-243).
 *
 * The name branch carries the validation result itself
 * ([CatalogNames.Validation.Invalid]). The originating throwable of a failed
 * write goes once to the diagnostics channel ([LoggingPort.error]) at the
 * use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates.
 */
sealed interface RenameEquipmentResult {
    data object Success : RenameEquipmentResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) :
        RenameEquipmentResult

    /** The equipment write failed (repository / store I/O). */
    data object PersistenceFailed : RenameEquipmentResult
}

/** Renames an equipment row. Seed and custom equipment may both be renamed by the user. */
class RenameEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: EquipmentId, name: String): RenameEquipmentResult {
        val trimmed =
            when (val nameResult = CatalogNames.validateEquipmentName(name)) {
                is CatalogNames.Validation.Valid -> nameResult.name
                is CatalogNames.Validation.Invalid ->
                    return RenameEquipmentResult.InvalidName(nameResult)
            }
        return try {
            repository.renameEquipment(id, trimmed)
            RenameEquipmentResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to rename equipment: ${t.message}", t)
            RenameEquipmentResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "RenameEquipment"
    }
}
