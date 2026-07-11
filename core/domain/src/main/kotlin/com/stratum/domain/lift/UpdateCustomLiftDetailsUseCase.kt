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
        if (details.movementPatterns.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("At least one movement pattern is required"),
            )
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to update custom lift details") {
            val lift =
                repository.observeLifts().first().firstOrNull { it.id == id }
                    ?: error("Lift not found")
            require(lift.isCustom) { "Catalog lifts cannot be edited" }
            repository.updateCustomLiftDetails(id, details)
        }
    }

    private companion object {
        const val TAG = "UpdateCustomLiftDetails"
    }
}
