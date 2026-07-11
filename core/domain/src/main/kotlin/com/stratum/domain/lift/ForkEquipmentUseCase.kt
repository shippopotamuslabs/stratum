package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Copies an existing equipment row's default tiers into a new custom equipment entry.
 *
 * Parent may be seed or custom. Does **not** create lineage / `forkedFrom` linkage — the
 * new row is standalone. Does not reassign lifts; callers assign lifts separately.
 */
class ForkEquipmentUseCase @Inject constructor(
    private val equipmentRepository: EquipmentRepository,
    private val validate: ValidateIncrementTiersUseCase,
    private val loggingPort: LoggingPort,
) {
    /**
     * Creates custom equipment named [newName] with a deep copy of [sourceEquipmentId]'s
     * current default tiers. Empty parent tiers become the repository's default single band.
     */
    suspend operator fun invoke(
        sourceEquipmentId: EquipmentId,
        newName: String,
    ): Result<EquipmentId> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Equipment name is required"))
        }
        val parent =
            equipmentRepository.getEquipment(sourceEquipmentId)
                ?: return Result.failure(IllegalArgumentException("Equipment not found"))
        val tiers = parent.incrementTiers
        if (tiers.isNotEmpty()) {
            when (val result = validate(tiers)) {
                is IncrementTiersValidationResult.Invalid ->
                    return Result.failure(IncrementTiersInvalidException(result.errors))
                IncrementTiersValidationResult.Valid -> Unit
            }
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to fork equipment") {
            equipmentRepository.addCustomEquipment(trimmed, tiers)
        }
    }

    private companion object {
        const val TAG = "ForkEquipment"
    }
}
