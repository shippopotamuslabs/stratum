package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import com.stratum.domain.lift.IncrementLattice
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight

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
        // Constructing the lattice is the validation (STR-165).
        IncrementLattice.resultOf(tiers, micro).onFailure { return Result.failure(it) }
        return runCatchingLogged(loggingPort, TAG, "Failed to set equipment increment tiers") {
            repository.setIncrementTiers(id, tiers, micro)
        }
    }

    private companion object {
        const val TAG = "SetEquipmentIncrementTiers"
    }
}
