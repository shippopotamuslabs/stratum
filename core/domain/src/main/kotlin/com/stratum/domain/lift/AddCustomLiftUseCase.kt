package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

/**
 * Creates a user-defined lift in the catalog from the Create Lift screen (or equivalent write
 * path). Validates required fields before calling [LiftRepository.addCustomLift].
 */
class AddCustomLiftUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(definition: CustomLiftDefinition): Result<LiftId> {
        val name = definition.name.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("Lift name is required"))
        }
        if (definition.movementPatterns.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("At least one movement pattern is required"),
            )
        }
        if (definition.primaryMuscles.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("At least one primary muscle is required"),
            )
        }
        val rest = definition.defaultRestSeconds
        if (rest != null &&
            (rest < SetLiftDefaultRestUseCase.MIN_REST_SECONDS ||
                rest > SetLiftDefaultRestUseCase.MAX_REST_SECONDS)
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Rest must be between ${SetLiftDefaultRestUseCase.MIN_REST_SECONDS} and " +
                        "${SetLiftDefaultRestUseCase.MAX_REST_SECONDS} seconds",
                ),
            )
        }
        val equipment =
            definition.equipment
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val secondary = definition.secondaryMuscles - definition.primaryMuscles
        return runCatching {
            repository.addCustomLift(
                definition.copy(
                    name = name,
                    secondaryMuscles = secondary,
                    equipment = equipment,
                ),
            )
        }
    }
}
