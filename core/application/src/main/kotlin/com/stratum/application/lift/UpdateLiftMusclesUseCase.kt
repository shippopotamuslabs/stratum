package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
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
 * write rides the [PersistenceFailed] branch as an [OperationalDiagnostic]
 * (legacy tag + rendered message, real throwable as cause); the presentation
 * boundary drains it exactly once through its module's DiagnosticDrain.
 * Coroutine cancellation is not a branch: it propagates.
 */
sealed interface UpdateLiftMusclesResult {
    data object Success : UpdateLiftMusclesResult

    data class InvalidField(val validation: CustomLiftFieldValidation.Invalid) :
        UpdateLiftMusclesResult

    data class LiftNotFound(val liftId: LiftId) : UpdateLiftMusclesResult

    data class CatalogLiftNotEditable(val liftId: LiftId) : UpdateLiftMusclesResult

    /** The catalog read or the muscles write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : UpdateLiftMusclesResult
}

/**
 * Updates a **custom** lift's primary/secondary muscle tags from the Gym Information → Details
 * tab. Requires at least one primary muscle so volume accounting stays well-defined. Catalog
 * (seed) lifts are immutable — muscle edits are rejected.
 */
class UpdateLiftMusclesUseCase @Inject constructor(
    private val repository: LiftRepository,
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
            UpdateLiftMusclesResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to update lift muscles: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "UpdateLiftMuscles"
    }
}
