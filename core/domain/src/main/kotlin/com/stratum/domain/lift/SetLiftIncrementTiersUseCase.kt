package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Validates then persists a lift's **override** increment-tier bands. Used by the Gym
 * lift-config Increments screen. Empty tiers are allowed (Valid) and clear the lift-scoped
 * override so the lift re-inherits equipment defaults (or global fallback).
 */
class SetLiftIncrementTiersUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val validate: ValidateIncrementTiersUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: LiftId,
        tiers: List<IncrementTier>,
    ): Result<Unit> {
        when (val result = validate(tiers)) {
            is IncrementTiersValidationResult.Invalid ->
                return Result.failure(IncrementTiersInvalidException(result.errors))
            IncrementTiersValidationResult.Valid -> Unit
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
