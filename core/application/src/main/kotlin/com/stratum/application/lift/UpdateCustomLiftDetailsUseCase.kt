package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
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
 * write goes once to the diagnostics channel ([LoggingPort.error]) at the
 * use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates.
 */
sealed interface UpdateCustomLiftDetailsResult {
    data object Success : UpdateCustomLiftDetailsResult

    data class InvalidField(val validation: CustomLiftFieldValidation.Invalid) :
        UpdateCustomLiftDetailsResult

    data class LiftNotFound(val liftId: LiftId) : UpdateCustomLiftDetailsResult

    data class CatalogLiftNotEditable(val liftId: LiftId) : UpdateCustomLiftDetailsResult

    /** The catalog read or the details write failed (repository / store I/O). */
    data object PersistenceFailed : UpdateCustomLiftDetailsResult
}

/**
 * Updates non-derived metadata on a **custom** lift from the Gym Information → Details tab.
 * Catalog (seed) lifts are immutable here; category stays derived and is never written.
 */
class UpdateCustomLiftDetailsUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
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
            loggingPort.error(TAG, "Failed to update custom lift details: ${t.message}", t)
            UpdateCustomLiftDetailsResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "UpdateCustomLiftDetails"
    }
}
