package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import kotlinx.coroutines.flow.first

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
    ): Result<ForkedEquipment> {
        val trimmed = CatalogNames.equipmentNameResult(newName).getOrElse { return Result.failure(it) }
        val parent =
            equipmentRepository.getEquipment(sourceEquipmentId)
                ?: return Result.failure(IllegalArgumentException("Equipment not found"))
        val tiers = parent.incrementTiers
        // Fork semantics live on the model (STR-169): null inherits the parent's stops.
        val resolvedMicro = parent.resolvedForkMicro(micro)
        // Constructing the lattice is the validation (STR-165). Tier rules always
        // apply to the parent's bands; micro rules apply only on the source-check
        // path (explicit stops for replacement tiers are validated at the
        // destination write instead).
        if (tiers.isNotEmpty()) {
            val tierErrors = IncrementLattice.tierErrors(tiers)
            if (tierErrors.isNotEmpty()) {
                return Result.failure(IncrementTiersInvalidException(tierErrors))
            }
        }
        if (validateMicroAgainstSource && resolvedMicro.isNotEmpty()) {
            val microErrors = IncrementLattice.microErrors(tiers, resolvedMicro)
            if (microErrors.isNotEmpty()) {
                return Result.failure(MicroIncrementsInvalidException(microErrors))
            }
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to fork equipment") {
            // STR-246: the saved name is the requested name when unique; on a collision
            // the shared policy appends the lowest free numbered suffix — never "(copy)".
            val liveNames =
                equipmentRepository.observeEquipment().first()
                    .filterNot { it.isTombstoned }
                    .map { it.name }
            val resolvedName = uniqueifyForkEquipmentName(trimmed, liveNames)
            val id = equipmentRepository.addCustomEquipment(resolvedName, tiers, resolvedMicro)
            ForkedEquipment(id, resolvedMicro)
        }
    }

    private companion object {
        const val TAG = "ForkEquipment"
    }
}
