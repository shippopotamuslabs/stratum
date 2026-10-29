package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.onboarding.MuscleGroup
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.stratum.application.lift.CustomLiftFieldRules
import com.stratum.domain.lift.Lift

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
        CustomLiftFieldRules.validatePrimaryMuscles(primaryMuscles)
            .onFailure { return Result.failure(it) }
        val lift =
            repository.observeLifts().first().firstOrNull { it.id == id }
                ?: return Result.failure(IllegalArgumentException("Lift not found"))
        if (!lift.isCustom) {
            return Result.failure(IllegalStateException("Catalog lifts cannot be edited"))
        }
        val secondary = CustomLiftFieldRules.distinctSecondaryMuscles(primaryMuscles, secondaryMuscles)
        return runCatchingLogged(loggingPort, TAG, "Failed to update lift muscles") {
            repository.updateLiftMuscles(id, primaryMuscles, secondary)
        }
    }

    private companion object {
        const val TAG = "UpdateLiftMuscles"
    }
}
