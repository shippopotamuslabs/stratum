package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Builds the [IncrementLattice] (which validates) then persists equipment default
 * increment tiers and adder stops. Empty tiers
 * clear equipment defaults so dependent lifts without overrides fall back to global
 * fallback; empty [micro] means no adder.
 */
class SetEquipmentIncrementTiersUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: EquipmentId,
        tiers: List<IncrementTier>,
        micro: List<Weight> = emptyList(),
    ): Result<Unit> {
        // Constructing the lattice is the validation (STR-165): it throws the same
        // invalid-tier / invalid-micro exceptions the validators used to produce.
        try {
            IncrementLattice(tiers, micro)
        } catch (e: IncrementTiersInvalidException) {
            return Result.failure(e)
        } catch (e: MicroIncrementsInvalidException) {
            return Result.failure(e)
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to set equipment increment tiers") {
            repository.setIncrementTiers(id, tiers, micro)
        }
    }

    private companion object {
        const val TAG = "SetEquipmentIncrementTiers"
    }
}
