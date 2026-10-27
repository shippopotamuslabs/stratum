package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Updates non-derived metadata on a **custom** lift from the Gym Information → Details tab.
 * Catalog (seed) lifts are immutable here; category stays derived and is never written.
 */
class UpdateCustomLiftDetailsUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: LiftId,
        details: CustomLiftDetails,
    ): Result<Unit> {
        CustomLiftFieldRules.validateMovementPatterns(details.movementPatterns)
            .onFailure { return Result.failure(it) }
        val lift =
            repository.observeLifts().first().firstOrNull { it.id == id }
                ?: return Result.failure(IllegalArgumentException("Lift not found"))
        if (!lift.isCustom) {
            return Result.failure(IllegalStateException("Catalog lifts cannot be edited"))
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to update custom lift details") {
            repository.updateCustomLiftDetails(id, details)
        }
    }

    private companion object {
        const val TAG = "UpdateCustomLiftDetails"
    }
}
