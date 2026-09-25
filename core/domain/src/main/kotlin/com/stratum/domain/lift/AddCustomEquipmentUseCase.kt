package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Creates user-defined equipment with optional default increment tiers. Validates the name and
 * any provided tiers before persisting. [micro] seeds the adder stops (empty = no adder);
 * a clone may or may not carry the knob.
 */
class AddCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val validate: ValidateIncrementTiersUseCase,
    private val validateMicro: ValidateMicroIncrementsUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        name: String,
        tiers: List<IncrementTier> = emptyList(),
        micro: List<Weight> = emptyList(),
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
        // Micro validates against the persisted context: empty tiers become the
        // repository's default single band on write, so stops legal for that band
        // (e.g. ≤ 5 lb) must not be rejected as MicroWithoutTiers here.
        val effectiveTiers = tiers.ifEmpty { defaultSingleTiers() }
        if (micro.isNotEmpty()) {
            when (val result = validateMicro(effectiveTiers, micro)) {
                is MicroIncrementsValidationResult.Invalid ->
                    return Result.failure(MicroIncrementsInvalidException(result.errors))
                MicroIncrementsValidationResult.Valid -> Unit
            }
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to add custom equipment") {
            repository.addCustomEquipment(trimmed, tiers, micro)
        }
    }

    private companion object {
        const val TAG = "AddCustomEquipment"
    }
}
