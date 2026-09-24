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
 * @param micro optional lift-scoped adder override validated against [tiers]: null
 * leaves the micro column untouched, non-null replaces it (empty = explicitly no
 * adder). Independent of the band override — a lift can override bands while
 * inheriting the equipment knob and vice versa.
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
        micro: List<Weight>? = null,
    ): Result<Unit> {
        when (val result = validate(tiers)) {
            is IncrementTiersValidationResult.Invalid ->
                return Result.failure(IncrementTiersInvalidException(result.errors))
            IncrementTiersValidationResult.Valid -> Unit
        }
        if (micro != null) {
            when (val result = validateMicro(tiers, micro)) {
                is MicroIncrementsValidationResult.Invalid ->
                    return Result.failure(MicroIncrementsInvalidException(result.errors))
                MicroIncrementsValidationResult.Valid -> Unit
            }
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to set increment tiers") {
            repository.setIncrementTiers(id, tiers)
            if (micro != null) {
                repository.setMicroOverride(id, micro)
            }
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
