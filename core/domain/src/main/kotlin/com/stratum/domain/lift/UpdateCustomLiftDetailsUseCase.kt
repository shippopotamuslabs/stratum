package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Updates non-derived metadata on a **custom** lift from the Gym Information → Details tab.
 * Catalog (seed) lifts are immutable here; category stays derived and is never written.
 */
class UpdateCustomLiftDetailsUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(
        id: LiftId,
        details: CustomLiftDetails,
    ): Result<Unit> {
        if (details.movementPatterns.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("At least one movement pattern is required"),
            )
        }
        val lift =
            repository.observeLifts().first().firstOrNull { it.id == id }
                ?: return Result.failure(IllegalArgumentException("Lift not found"))
        if (!lift.isCustom) {
            return Result.failure(
                IllegalArgumentException("Catalog lifts cannot be edited"),
            )
        }
        val equipment =
            details.equipment
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        repository.updateCustomLiftDetails(
            id,
            details.copy(equipment = equipment),
        )
        return Result.success(Unit)
    }
}
