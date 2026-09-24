package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Validates then persists a lift's **override** increment-tier bands. Used by the Gym
 * lift-config Increments screen. Empty tiers are allowed (Valid) and clear the lift-scoped
 * override so the lift re-inherits equipment defaults (or global fallback).
 *
 * @param micro lift-scoped adder override validated against [tiers]; micro persistence
 * lands with the Room v7 columns — until then this validates the draft so no invalid
 * stops can flow into the editors.
 */
class SetLiftIncrementTiersUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val validate: ValidateIncrementTiersUseCase,
    private val validateMicro: ValidateMicroIncrementsUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: LiftId,
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
        return runCatchingLogged(loggingPort, TAG, "Failed to set increment tiers") {
            repository.setIncrementTiers(id, tiers)
        }
    }

    private companion object {
        const val TAG = "SetLiftIncrementTiers"
    }
}

/** Thrown when [SetLiftIncrementTiersUseCase] rejects a non-contiguous / malformed tier list. */
class IncrementTiersInvalidException(
    val errors: Set<IncrementTiersValidationError>,
) : Exception("Invalid increment tiers: $errors")
