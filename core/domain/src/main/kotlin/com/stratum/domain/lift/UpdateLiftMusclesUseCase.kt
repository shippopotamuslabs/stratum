package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.onboarding.MuscleGroup
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Updates a **custom** lift's primary/secondary muscle tags from the Gym Information → Details
 * tab. Requires at least one primary muscle so volume accounting stays well-defined. Catalog
 * (seed) lifts are immutable — muscle edits are rejected.
 */
class UpdateLiftMusclesUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: LiftId,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
    ): Result<Unit> {
        if (primaryMuscles.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one primary muscle is required"))
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to update lift muscles") {
            val lift =
                repository.observeLifts().first().firstOrNull { it.id == id }
                    ?: error("Lift not found")
            require(lift.isCustom) { "Catalog lifts cannot be edited" }
            // Secondary must not overlap primary (primary wins).
            val secondary = secondaryMuscles - primaryMuscles
            repository.updateLiftMuscles(id, primaryMuscles, secondary)
        }
    }

    private companion object {
        const val TAG = "UpdateLiftMuscles"
    }
}
