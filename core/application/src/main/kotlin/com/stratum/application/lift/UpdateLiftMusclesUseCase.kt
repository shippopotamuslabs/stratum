package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.onboarding.MuscleGroup
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.stratum.domain.lift.Lift

/**
 * Outcome of updating a custom lift's muscles (STR-243).
 *
 * The field branch carries the validation result itself
 * ([CustomLiftFieldValidation.Invalid]); [CatalogLiftNotEditable] is the
 * catalog-immutability refusal. The originating throwable of a failed read or
 * write goes once to the diagnostics channel ([LoggingPort.error]) at the
 * use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates.
 */
sealed interface UpdateLiftMusclesResult {
    data object Success : UpdateLiftMusclesResult

    data class InvalidField(val validation: CustomLiftFieldValidation.Invalid) :
        UpdateLiftMusclesResult

    data class LiftNotFound(val liftId: LiftId) : UpdateLiftMusclesResult

    data class CatalogLiftNotEditable(val liftId: LiftId) : UpdateLiftMusclesResult

    /** The catalog read or the muscles write failed (repository / store I/O). */
    data object PersistenceFailed : UpdateLiftMusclesResult
}

/**
 * Updates a **custom** lift's primary/secondary muscle tags from the Gym Information → Details
 * tab. Requires at least one primary muscle so volume accounting stays well-defined. Catalog
 * (seed) lifts are immutable — muscle edits are rejected.
 */
class UpdateLiftMusclesUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: LiftId,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
    ): UpdateLiftMusclesResult {
        (CustomLiftFieldRules.validatePrimaryMuscles(primaryMuscles)
            as? CustomLiftFieldValidation.Invalid)
            ?.let { return UpdateLiftMusclesResult.InvalidField(it) }
        return try {
            val lift =
                repository.observeLifts().first().firstOrNull { it.id == id }
                    ?: return UpdateLiftMusclesResult.LiftNotFound(id)
            if (!lift.isCustom) {
                return UpdateLiftMusclesResult.CatalogLiftNotEditable(id)
            }
            val secondary =
                CustomLiftFieldRules.distinctSecondaryMuscles(primaryMuscles, secondaryMuscles)
            repository.updateLiftMuscles(id, primaryMuscles, secondary)
            UpdateLiftMusclesResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to update lift muscles: ${t.message}", t)
            UpdateLiftMusclesResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "UpdateLiftMuscles"
    }
}
