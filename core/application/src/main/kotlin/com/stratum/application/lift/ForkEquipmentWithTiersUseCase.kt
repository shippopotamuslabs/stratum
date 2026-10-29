package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight

/**
 * Forks [sourceId] under [copyName] carrying [tiers] and [micro], leaving the shared
 * defaults untouched; reassigns [liftId] to the copy when provided so it inherits
 * the edit.
 *
 * Sequential with first-failure return (no cross-repository transaction). The copy's
 * knob is resolved once from the fork result — explicit [micro] replaces the parent's
 * stops (empty = no knob), null inherits them — and the same value overwrites the copy
 * alongside [tiers]. No re-read: a missing copy surfaces as the fork failure, never as
 * an empty stop list that would silently drop inherited stops.
 *
 * @param micro adder stops for the copy; null keeps what the fork carried over from
 * the parent (a clone starts with the same knob), non-null replaces them. Explicit
 * stops target the new [tiers], so they skip source-tier validation here and are
 * validated against the destination bands by [setEquipmentIncrementTiers].
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
        micro: List<Weight>? = null,
    ): Result<EquipmentId> {
        val forked =
            forkEquipment(
                sourceId,
                copyName,
                micro,
                validateMicroAgainstSource = micro == null,
            ).getOrElse { return Result.failure(it) }
        setEquipmentIncrementTiers(forked.id, tiers, forked.microIncrements).onFailure {
            return Result.failure(it)
        }
        if (liftId != null) {
            setLiftEquipment(liftId, forked.id).onFailure { return Result.failure(it) }
        }
        return Result.success(forked.id)
    }
}
