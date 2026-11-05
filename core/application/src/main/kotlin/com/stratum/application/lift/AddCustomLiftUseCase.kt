package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.Lift

/**
 * Outcome of adding a custom lift (STR-243; STR-237 Route (i)).
 *
 * Validation branches carry the validation results themselves
 * ([CatalogNames.Validation.Invalid] for the name rule,
 * [CustomLiftFieldValidation.Invalid] for the field rules). The originating
 * throwable of a failed write rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface AddCustomLiftResult {
    data class Success(val liftId: LiftId) : AddCustomLiftResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) : AddCustomLiftResult

    data class InvalidField(val validation: CustomLiftFieldValidation.Invalid) : AddCustomLiftResult

    /** The lift write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : AddCustomLiftResult
}

/**
 * Creates a user-defined lift in the catalog from the Create Lift screen (or equivalent write
 * path). Validates required fields before calling [LiftRepository.addCustomLift].
 */
class AddCustomLiftUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(definition: CustomLiftDefinition): AddCustomLiftResult {
        // Name rule and field rules are owned once on the domain side (STR-169);
        // the outcome branches carry their typed validation results directly.
        val name =
            when (val nameResult = CatalogNames.validateLiftName(definition.name)) {
                is CatalogNames.Validation.Valid -> nameResult.name
                is CatalogNames.Validation.Invalid ->
                    return AddCustomLiftResult.InvalidName(nameResult)
            }
        (CustomLiftFieldRules.validateMovementPatterns(definition.movementPatterns)
            as? CustomLiftFieldValidation.Invalid)
            ?.let { return AddCustomLiftResult.InvalidField(it) }
        (CustomLiftFieldRules.validatePrimaryMuscles(definition.primaryMuscles)
            as? CustomLiftFieldValidation.Invalid)
            ?.let { return AddCustomLiftResult.InvalidField(it) }
        (CustomLiftFieldRules.validateDefaultRestSeconds(definition.defaultRestSeconds)
            as? CustomLiftFieldValidation.Invalid)
            ?.let { return AddCustomLiftResult.InvalidField(it) }
        val equipmentName = CatalogNames.normalizedEquipmentNameOrNull(definition.equipmentName)
        val secondary =
            CustomLiftFieldRules.distinctSecondaryMuscles(
                definition.primaryMuscles,
                definition.secondaryMuscles,
            )
        return try {
            AddCustomLiftResult.Success(
                repository.addCustomLift(
                    definition.copy(
                        name = name,
                        secondaryMuscles = secondary,
                        equipmentName = equipmentName,
                    ),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            AddCustomLiftResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to add custom lift: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "AddCustomLift"
    }
}
