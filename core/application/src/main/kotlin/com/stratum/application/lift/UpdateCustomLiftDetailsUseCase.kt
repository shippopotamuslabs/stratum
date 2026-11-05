package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.Lift

/**
 * Outcome of updating a custom lift's details (STR-243).
 *
 * The field branch carries the validation result itself
 * ([CustomLiftFieldValidation.Invalid]); [CatalogLiftNotEditable] is the
 * catalog-immutability refusal. The originating throwable of a failed read or
 * write rides the [PersistenceFailed] branch as an [OperationalDiagnostic]
 * (legacy tag + rendered message, real throwable as cause); the presentation
 * boundary drains it exactly once through its module's DiagnosticDrain.
 * Coroutine cancellation is not a branch: it propagates.
 */
sealed interface UpdateCustomLiftDetailsResult {
    data object Success : UpdateCustomLiftDetailsResult

    data class InvalidField(val validation: CustomLiftFieldValidation.Invalid) :
        UpdateCustomLiftDetailsResult

    data class LiftNotFound(val liftId: LiftId) : UpdateCustomLiftDetailsResult

    data class CatalogLiftNotEditable(val liftId: LiftId) : UpdateCustomLiftDetailsResult

    /** The catalog read or the details write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : UpdateCustomLiftDetailsResult
}

/**
 * Updates non-derived metadata on a **custom** lift from the Gym Information → Details tab.
 * Catalog (seed) lifts are immutable here; category stays derived and is never written.
 */
class UpdateCustomLiftDetailsUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(
        id: LiftId,
        details: CustomLiftDetails,
    ): UpdateCustomLiftDetailsResult {
        (CustomLiftFieldRules.validateMovementPatterns(details.movementPatterns)
            as? CustomLiftFieldValidation.Invalid)
            ?.let { return UpdateCustomLiftDetailsResult.InvalidField(it) }
        return try {
            val lift =
                repository.observeLifts().first().firstOrNull { it.id == id }
                    ?: return UpdateCustomLiftDetailsResult.LiftNotFound(id)
            if (!lift.isCustom) {
                return UpdateCustomLiftDetailsResult.CatalogLiftNotEditable(id)
            }
            repository.updateCustomLiftDetails(id, details)
            UpdateCustomLiftDetailsResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            UpdateCustomLiftDetailsResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to update custom lift details: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "UpdateCustomLiftDetails"
    }
}
