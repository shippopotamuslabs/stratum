package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.application.lift.CustomLiftFieldRules
import com.stratum.domain.lift.Lift

/**
 * Creates a user-defined lift in the catalog from the Create Lift screen (or equivalent write
 * path). Validates required fields before calling [LiftRepository.addCustomLift].
 */
class AddCustomLiftUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(definition: CustomLiftDefinition): Result<LiftId> {
        // Name rule and field rules are owned once on the domain side (STR-169).
        // STR-243 C3 minimal caller touch (this file converts in C4): the name and
        // field rules now return typed validations; map an Invalid back onto this
        // use case's Result failure with the same message, as before.
        val name =
            when (val nameResult = CatalogNames.validateLiftName(definition.name)) {
                is CatalogNames.Validation.Valid -> nameResult.name
                is CatalogNames.Validation.Invalid ->
                    return Result.failure(IllegalArgumentException(nameResult.message))
            }
        (CustomLiftFieldRules.validateMovementPatterns(definition.movementPatterns)
            as? CustomLiftFieldValidation.Invalid)
            ?.let { return Result.failure(IllegalArgumentException(it.message)) }
        (CustomLiftFieldRules.validatePrimaryMuscles(definition.primaryMuscles)
            as? CustomLiftFieldValidation.Invalid)
            ?.let { return Result.failure(IllegalArgumentException(it.message)) }
        (CustomLiftFieldRules.validateDefaultRestSeconds(definition.defaultRestSeconds)
            as? CustomLiftFieldValidation.Invalid)
            ?.let { return Result.failure(IllegalArgumentException(it.message)) }
        val equipmentName = CatalogNames.normalizedEquipmentNameOrNull(definition.equipmentName)
        val secondary =
            CustomLiftFieldRules.distinctSecondaryMuscles(
                definition.primaryMuscles,
                definition.secondaryMuscles,
            )
        return runCatchingLogged(loggingPort, TAG, "Failed to add custom lift") {
            repository.addCustomLift(
                definition.copy(
                    name = name,
                    secondaryMuscles = secondary,
                    equipmentName = equipmentName,
                ),
            )
        }
    }

    private companion object {
        const val TAG = "AddCustomLift"
    }
}
