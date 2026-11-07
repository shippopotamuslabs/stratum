package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementLattice
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.MicroIncrementsValidationResult
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.uniqueifyForkEquipmentName

/**
 * Outcome of forking equipment (STR-243).
 *
 * Validation branches carry the validation results themselves
 * ([CatalogNames.Validation.Invalid], [IncrementTiersValidationResult.Invalid],
 * [MicroIncrementsValidationResult.Invalid]). The originating throwable of a failed
 * read or write goes once to the diagnostics channel ([LoggingPort.error]) at the
 * use-case boundary; it never crosses as a payload. Coroutine cancellation is not
 * a branch: it propagates as cancellation.
 */
sealed interface ForkEquipmentResult {
    data class Success(val forked: ForkedEquipment) : ForkEquipmentResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) : ForkEquipmentResult

    data class EquipmentNotFound(val equipmentId: EquipmentId) : ForkEquipmentResult

    data class InvalidTiers(val validation: IncrementTiersValidationResult.Invalid) :
        ForkEquipmentResult

    data class InvalidMicro(val validation: MicroIncrementsValidationResult.Invalid) :
        ForkEquipmentResult

    /** The parent read or the copy write failed (repository / store I/O). */
    data object PersistenceFailed : ForkEquipmentResult
}

/**
 * Copies an existing equipment row's default tiers into a new custom equipment entry.
 *
 * Parent may be seed or custom. Does **not** create lineage / `forkedFrom` linkage — the
 * new row is standalone. Does not reassign lifts; callers assign lifts separately.
 */

/**
 * The copy id plus the adder stops it carries, so callers that overwrite the copy's
 * tiers (e.g. fork-with-tiers) can reuse the resolved knob without re-reading the row.
 */
data class ForkedEquipment(
    val id: EquipmentId,
    val microIncrements: List<Weight>,
)
class ForkEquipmentUseCase @Inject constructor(
    private val equipmentRepository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    /**
     * Creates custom equipment named [newName] with a deep copy of [sourceEquipmentId]'s
     * current default tiers. Empty parent tiers become the repository's default single band.
     *
     * @param micro adder stops for the copy; null copies the parent's stops (the default —
     * a clone starts with the same knob), non-null replaces them (empty = no knob).
     * @param validateMicroAgainstSource skip when explicit [micro] targets replacement
     * tiers the caller writes after the fork (fork-with-tiers): stops valid for the new
     * bands but larger than the parent's largest step must not be rejected here — the
     * destination write validates them. The parent-inherit path keeps validating.
     */
    suspend operator fun invoke(
        sourceEquipmentId: EquipmentId,
        newName: String,
        micro: List<Weight>? = null,
        validateMicroAgainstSource: Boolean = true,
    ): ForkEquipmentResult {
        val trimmed =
            when (val nameResult = CatalogNames.validateEquipmentName(newName)) {
                is CatalogNames.Validation.Valid -> nameResult.name
                is CatalogNames.Validation.Invalid ->
                    return ForkEquipmentResult.InvalidName(nameResult)
            }
        return try {
            val parent =
                equipmentRepository.getEquipment(sourceEquipmentId)
                    ?: return ForkEquipmentResult.EquipmentNotFound(sourceEquipmentId)
            val tiers = parent.incrementTiers
            // Fork semantics live on the model (STR-169): null inherits the parent's stops.
            val resolvedMicro = parent.resolvedForkMicro(micro)
            // The lattice rules are the validation (STR-165). Tier rules always
            // apply to the parent's bands; micro rules apply only on the source-check
            // path (explicit stops for replacement tiers are validated at the
            // destination write instead).
            if (tiers.isNotEmpty()) {
                when (val tiersResult = IncrementLattice.validateTiers(tiers)) {
                    is IncrementTiersValidationResult.Invalid ->
                        return ForkEquipmentResult.InvalidTiers(tiersResult)
                    IncrementTiersValidationResult.Valid -> Unit
                }
            }
            if (validateMicroAgainstSource && resolvedMicro.isNotEmpty()) {
                when (val microResult = IncrementLattice.validateMicro(tiers, resolvedMicro)) {
                    is MicroIncrementsValidationResult.Invalid ->
                        return ForkEquipmentResult.InvalidMicro(microResult)
                    MicroIncrementsValidationResult.Valid -> Unit
                }
            }
            // STR-246: the saved name is the requested name when unique; on a collision
            // the shared policy appends the lowest free numbered suffix — never "(copy)".
            val liveNames =
                equipmentRepository.observeEquipment().first()
                    .filterNot { it.isTombstoned }
                    .map { it.name }
            val resolvedName = uniqueifyForkEquipmentName(trimmed, liveNames)
            val id = equipmentRepository.addCustomEquipment(resolvedName, tiers, resolvedMicro)
            ForkEquipmentResult.Success(ForkedEquipment(id, resolvedMicro))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to fork equipment: ${t.message}", t)
            ForkEquipmentResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "ForkEquipment"
    }
}
