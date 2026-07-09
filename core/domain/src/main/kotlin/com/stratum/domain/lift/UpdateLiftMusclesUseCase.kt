package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
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
) {
    suspend operator fun invoke(
        id: LiftId,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
    ): Result<Unit> {
        if (primaryMuscles.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one primary muscle is required"))
        }
        val lift =
            repository.observeLifts().first().firstOrNull { it.id == id }
                ?: return Result.failure(IllegalArgumentException("Lift not found"))
        if (!lift.isCustom) {
            return Result.failure(
                IllegalArgumentException("Catalog lifts cannot be edited"),
            )
        }
        // Secondary must not overlap primary (primary wins).
        val secondary = secondaryMuscles - primaryMuscles
        repository.updateLiftMuscles(id, primaryMuscles, secondary)
        return Result.success(Unit)
    }
}
