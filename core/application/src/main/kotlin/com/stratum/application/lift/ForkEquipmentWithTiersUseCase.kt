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
 * branches. The lift-reassignment step's failure kinds are enumerated on the
 * `Assignment*` branches, mapped one-to-one from [SetLiftEquipmentResult]
 * (STR-243 C4 retired the interim single `LiftAssignmentFailed` branch once
 * that use case returned typed outcomes; its operational failures are logged
 * once at its own boundary).
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

    /** The lift-reassignment step's lift was not found. */
    data class AssignmentLiftNotFound(val liftId: LiftId) : ForkEquipmentWithTiersResult

    /** The lift-reassignment step targeted deleted equipment. */
    data class AssignmentEquipmentDeleted(val equipmentId: EquipmentId) :
        ForkEquipmentWithTiersResult

    /** The lift-reassignment step's read or write failed. */
    data object AssignmentPersistenceFailed : ForkEquipmentWithTiersResult
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
            setLiftEquipment(liftId, forked.id).asAssignmentFailure()?.let { return it }
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

    /** Maps an assignment failure onto this composite's matching branch; null on success. */
    private fun SetLiftEquipmentResult.asAssignmentFailure(): ForkEquipmentWithTiersResult? =
        when (this) {
            SetLiftEquipmentResult.Success -> null
            is SetLiftEquipmentResult.LiftNotFound ->
                ForkEquipmentWithTiersResult.AssignmentLiftNotFound(liftId)
            is SetLiftEquipmentResult.EquipmentDeleted ->
                ForkEquipmentWithTiersResult.AssignmentEquipmentDeleted(equipmentId)
            SetLiftEquipmentResult.PersistenceFailed ->
                ForkEquipmentWithTiersResult.AssignmentPersistenceFailed
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
