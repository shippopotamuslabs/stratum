package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

/**
 * Validates then persists a lift's increment-tier bands. Used by the Gym lift-config
 * Increments screen. Empty tiers are allowed (Valid) and clear stored bands so progression
 * and program gen fall back to defaults.
 */
class SetLiftIncrementTiersUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val validate: ValidateIncrementTiersUseCase,
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
        repository.setIncrementTiers(id, tiers)
        return Result.success(Unit)
    }
}

/** Thrown when [SetLiftIncrementTiersUseCase] rejects a non-contiguous / malformed tier list. */
class IncrementTiersInvalidException(
    val errors: Set<IncrementTiersValidationError>,
) : Exception("Invalid increment tiers: $errors")
