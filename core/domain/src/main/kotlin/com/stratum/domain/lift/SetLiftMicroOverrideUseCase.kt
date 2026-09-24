package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Validates then persists a lift's **micro (adder) override** without touching its band
 * override. Null [micro] clears the override so the lift re-inherits the linked
 * equipment's stops; empty means explicitly no adder. Used by the reset-to-equipment
 * path (null) and the lift editor's adder section.
 */
class SetLiftMicroOverrideUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val validateMicro: ValidateMicroIncrementsUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: LiftId,
        tiers: List<IncrementTier>,
        micro: List<Weight>?,
    ): Result<Unit> {
        if (micro != null) {
            when (val result = validateMicro(tiers, micro)) {
                is MicroIncrementsValidationResult.Invalid ->
                    return Result.failure(MicroIncrementsInvalidException(result.errors))
                MicroIncrementsValidationResult.Valid -> Unit
            }
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to set lift micro override") {
            repository.setMicroOverride(id, micro)
        }
    }

    private companion object {
        const val TAG = "SetLiftMicroOverride"
    }
}
