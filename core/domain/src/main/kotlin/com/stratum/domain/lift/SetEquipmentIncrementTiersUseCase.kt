package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Validates then persists equipment default increment tiers. Empty tiers clear equipment
 * defaults so dependent lifts without overrides fall back to global defaults.
 *
 * @param micro adder stops validated against [tiers]; micro persistence lands with the
 * Room v7 columns — until then this validates the draft so no invalid stops can flow
 * into the editors.
 */
class SetEquipmentIncrementTiersUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val validate: ValidateIncrementTiersUseCase,
    private val validateMicro: ValidateMicroIncrementsUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: EquipmentId,
        tiers: List<IncrementTier>,
        micro: List<Weight> = emptyList(),
    ): Result<Unit> {
        when (val result = validate(tiers)) {
            is IncrementTiersValidationResult.Invalid ->
                return Result.failure(IncrementTiersInvalidException(result.errors))
            IncrementTiersValidationResult.Valid -> Unit
        }
        when (val result = validateMicro(tiers, micro)) {
            is MicroIncrementsValidationResult.Invalid ->
                return Result.failure(MicroIncrementsInvalidException(result.errors))
            MicroIncrementsValidationResult.Valid -> Unit
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to set equipment increment tiers") {
            repository.setIncrementTiers(id, tiers)
        }
    }

    private companion object {
        const val TAG = "SetEquipmentIncrementTiers"
    }
}
