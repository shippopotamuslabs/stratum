package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.IncrementLattice
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.defaultSingleTiers

/**
 * Creates user-defined equipment with optional default increment tiers. Validates the name and
 * any provided tiers (by constructing their [IncrementLattice]) before persisting. [micro] seeds the adder stops (empty = no adder);
 * a clone may or may not carry the knob.
 */
class AddCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        name: String,
        tiers: List<IncrementTier> = emptyList(),
        micro: List<Weight> = emptyList(),
    ): Result<EquipmentId> {
        val trimmed = CatalogNames.equipmentNameResult(name).getOrElse { return Result.failure(it) }
        // Constructing the lattice is the validation (STR-165). Micro validates
        // against the persisted context: empty tiers become the repository's default
        // single band on write, so stops legal for that band (e.g. ≤ 5 lb) must not
        // be rejected as MicroWithoutTiers here.
        val effectiveTiers = tiers.ifEmpty { defaultSingleTiers() }
        if (tiers.isNotEmpty() || micro.isNotEmpty()) {
            IncrementLattice.resultOf(effectiveTiers, micro).onFailure {
                return Result.failure(it)
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
