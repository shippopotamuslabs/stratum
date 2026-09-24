package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Validates then persists equipment default increment tiers. Empty tiers clear equipment
 * defaults so dependent lifts without overrides fall back to global defaults.
 *
 * Micro-adjust (adder) stops are validated separately via [ValidateMicroIncrementsUseCase]
 * for editor drafts; micro persistence lands with the Room v7 columns and is added here
 * atomically at that point (not as a validated-but-discarded parameter).
 */
class SetEquipmentIncrementTiersUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val validate: ValidateIncrementTiersUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: EquipmentId,
        tiers: List<IncrementTier>,
    ): Result<Unit> {
        when (val result = validate(tiers)) {
            is IncrementTiersValidationResult.Invalid ->
                return Result.failure(IncrementTiersInvalidException(result.errors))
            IncrementTiersValidationResult.Valid -> Unit
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to set equipment increment tiers") {
            repository.setIncrementTiers(id, tiers)
        }
    }

    private companion object {
        const val TAG = "SetEquipmentIncrementTiers"
    }
}
