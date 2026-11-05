package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.MicroIncrementsValidationResult
import com.stratum.domain.lift.Weight

/**
 * Outcome of the fork-with-tiers composite write (STR-243).
 *
 * The fork and tier-write steps surface their validation results on the matching
 * branches. [LiftAssignmentFailed] is the interim branch for the lift-reassignment
 * step: [SetLiftEquipmentUseCase] still returns a raw `Result` until its own cluster
 * (C4) converts it, so its failure kinds cannot be enumerated here yet; its
 * operational failures are already logged once at that use case's own boundary.
 */
sealed interface ForkEquipmentWithTiersResult {
    data class Success(val equipmentId: EquipmentId) : ForkEquipmentWithTiersResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) :
        ForkEquipmentWithTiersResult

    data class EquipmentNotFound(val equipmentId: EquipmentId) : ForkEquipmentWithTiersResult

    data class InvalidTiers(val validation: IncrementTiersValidationResult.Invalid) :
        ForkEquipmentWithTiersResult

    data class InvalidMicro(val validation: MicroIncrementsValidationResult.Invalid) :
        ForkEquipmentWithTiersResult

    /** A repository read or write in the fork or tier-write step failed. */
    data object PersistenceFailed : ForkEquipmentWithTiersResult

    /** The lift-reassignment step failed (see the type doc; interim until C4). */
    data object LiftAssignmentFailed : ForkEquipmentWithTiersResult
}

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
    ): ForkEquipmentWithTiersResult {
        val forkResult =
            forkEquipment(
                sourceId,
                copyName,
                micro,
                validateMicroAgainstSource = micro == null,
            )
        forkResult.asFailure()?.let { return it }
        val forked = (forkResult as ForkEquipmentResult.Success).forked
        setEquipmentIncrementTiers(forked.id, tiers, forked.microIncrements)
            .asFailure()
            ?.let { return it }
        if (liftId != null) {
            // Interim (see the outcome type doc): SetLiftEquipmentUseCase converts
            // in C4; until then any failure of the assignment step is this branch.
            if (setLiftEquipment(liftId, forked.id).isFailure) {
                return ForkEquipmentWithTiersResult.LiftAssignmentFailed
            }
        }
        return ForkEquipmentWithTiersResult.Success(forked.id)
    }

    /** Maps a fork failure onto this composite's matching branch; null on success. */
    private fun ForkEquipmentResult.asFailure(): ForkEquipmentWithTiersResult? =
        when (this) {
            is ForkEquipmentResult.Success -> null
            is ForkEquipmentResult.InvalidName ->
                ForkEquipmentWithTiersResult.InvalidName(validation)
            is ForkEquipmentResult.EquipmentNotFound ->
                ForkEquipmentWithTiersResult.EquipmentNotFound(equipmentId)
            is ForkEquipmentResult.InvalidTiers ->
                ForkEquipmentWithTiersResult.InvalidTiers(validation)
            is ForkEquipmentResult.InvalidMicro ->
                ForkEquipmentWithTiersResult.InvalidMicro(validation)
            ForkEquipmentResult.PersistenceFailed ->
                ForkEquipmentWithTiersResult.PersistenceFailed
        }

    /** Maps a tier-write failure onto this composite's matching branch; null on success. */
    private fun SetEquipmentIncrementTiersResult.asFailure(): ForkEquipmentWithTiersResult? =
        when (this) {
            SetEquipmentIncrementTiersResult.Success -> null
            is SetEquipmentIncrementTiersResult.InvalidTiers ->
                ForkEquipmentWithTiersResult.InvalidTiers(validation)
            is SetEquipmentIncrementTiersResult.InvalidMicro ->
                ForkEquipmentWithTiersResult.InvalidMicro(validation)
            SetEquipmentIncrementTiersResult.PersistenceFailed ->
                ForkEquipmentWithTiersResult.PersistenceFailed
        }
}
