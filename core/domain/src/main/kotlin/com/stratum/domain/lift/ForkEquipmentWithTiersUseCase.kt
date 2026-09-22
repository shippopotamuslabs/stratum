package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

/**
 * Forks [sourceId] under [copyName] carrying [tiers], leaving the shared defaults
 * untouched; reassigns [liftId] to the copy when provided so it inherits the edit.
 *
 * Sequential with first-failure return (no cross-repository transaction): the fork
 * validates the stored parent tiers, then the draft tiers overwrite the copy.
 */
class ForkEquipmentWithTiersUseCase @Inject constructor(
    private val forkEquipment: ForkEquipmentUseCase,
    private val setEquipmentIncrementTiers: SetEquipmentIncrementTiersUseCase,
    private val setLiftEquipment: SetLiftEquipmentUseCase,
) {
    suspend operator fun invoke(
        sourceId: EquipmentId,
        copyName: String,
        tiers: List<IncrementTier>,
        liftId: LiftId? = null,
    ): Result<EquipmentId> {
        val copyId = forkEquipment(sourceId, copyName).getOrElse { return Result.failure(it) }
        setEquipmentIncrementTiers(copyId, tiers).onFailure { return Result.failure(it) }
        if (liftId != null) {
            setLiftEquipment(liftId, copyId).onFailure { return Result.failure(it) }
        }
        return Result.success(copyId)
    }
}
