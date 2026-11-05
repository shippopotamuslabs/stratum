package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.CatalogNames

/**
 * Outcome of finding or creating equipment by name (STR-243; STR-237 Route (i)).
 *
 * The name branch carries the validation result itself
 * ([CatalogNames.Validation.Invalid]). The originating throwable of a failed
 * write rides the [PersistenceFailed] branch as an [OperationalDiagnostic]
 * (legacy tag + rendered message, real throwable as cause); the presentation
 * boundary drains it exactly once through its module's DiagnosticDrain.
 * Coroutine cancellation is not a branch: it propagates.
 */
sealed interface FindOrCreateEquipmentResult {
    data class Success(val equipmentId: EquipmentId) : FindOrCreateEquipmentResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) :
        FindOrCreateEquipmentResult

    /** The equipment read/write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : FindOrCreateEquipmentResult
}

/**
 * Finds equipment for a free-text label (exact / normalized / seed alias) or creates a custom
 * row with default single-band increments. Used when free-text equipment is entered on
 * create/import paths.
 */
class FindOrCreateEquipmentByNameUseCase @Inject constructor(
    private val repository: EquipmentRepository,
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
            FindOrCreateEquipmentResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to find or create equipment: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "FindOrCreateEquipment"
    }
}
