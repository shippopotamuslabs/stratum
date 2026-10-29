package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.IncrementLattice
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight

/**
 * Editor save that renames equipment and replaces its increment tiers as one
 * atomic write (STR-135): validates the name like [RenameEquipmentUseCase] and
 * the tiers like [SetEquipmentIncrementTiersUseCase], then persists both via a
 * single repository transaction so neither half can land without the other.
 */
class RenameAndSetIncrementTiersUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: EquipmentId,
        name: String,
        tiers: List<IncrementTier>,
        micro: List<Weight> = emptyList(),
    ): Result<Unit> {
        val trimmed = CatalogNames.equipmentNameResult(name).getOrElse { return Result.failure(it) }
        // Constructing the lattice is the validation (STR-165).
        IncrementLattice.resultOf(tiers, micro).onFailure { return Result.failure(it) }
        return runCatchingLogged(loggingPort, TAG, "Failed to rename equipment and set increment tiers") {
            repository.renameAndSetIncrementTiers(id, trimmed, tiers, micro)
        }
    }

    private companion object {
        const val TAG = "RenameAndSetIncrementTiers"
    }
}
