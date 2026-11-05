package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.CatalogNames

/**
 * Outcome of renaming equipment (STR-243; STR-237 Route (i)).
 *
 * The name branch carries the validation result itself
 * ([CatalogNames.Validation.Invalid]). The originating throwable of a failed
 * write rides the [PersistenceFailed] branch as an [OperationalDiagnostic]
 * (legacy tag + rendered message, real throwable as cause); the presentation
 * boundary drains it exactly once through its module's DiagnosticDrain.
 * Coroutine cancellation is not a branch: it propagates.
 */
sealed interface RenameEquipmentResult {
    data object Success : RenameEquipmentResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) :
        RenameEquipmentResult

    /** The equipment write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : RenameEquipmentResult
}

/** Renames an equipment row. Seed and custom equipment may both be renamed by the user. */
class RenameEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
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
            RenameEquipmentResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to rename equipment: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "RenameEquipment"
    }
}
