package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Creates user-defined equipment with optional default increment tiers. Validates the name and
 * any provided tiers before persisting.
 */
class AddCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val validate: ValidateIncrementTiersUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        name: String,
        tiers: List<IncrementTier> = emptyList(),
    ): Result<EquipmentId> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Equipment name is required"))
        }
        if (tiers.isNotEmpty()) {
            when (val result = validate(tiers)) {
                is IncrementTiersValidationResult.Invalid ->
                    return Result.failure(IncrementTiersInvalidException(result.errors))
                IncrementTiersValidationResult.Valid -> Unit
            }
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to add custom equipment") {
            repository.addCustomEquipment(trimmed, tiers)
        }
    }

    private companion object {
        const val TAG = "AddCustomEquipment"
    }
}
