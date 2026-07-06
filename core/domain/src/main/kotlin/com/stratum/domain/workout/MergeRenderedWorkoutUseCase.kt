package com.stratum.domain.workout

import com.stratum.domain.lift.Weight

/**
 * Pure merge use case (D1). Combines structure projection + snapshot + overlay into
 * the rendered workout read model.
 *
 * Merge priority: (a) logged value, (b) live recommendation for an upcoming set when
 * a newer per-set e1RM exists, (c) dynamic-set estimate. Overlay never writes back
 * into the snapshot.
 */
class MergeRenderedWorkoutUseCase @javax.inject.Inject constructor() {
    operator fun invoke(
        structure: WorkoutStructureProjection,
        snapshot: ProgressionSnapshot,
        overlay: WorkoutOverlay,
    ): RenderedWorkout {
        val lifts = structure.lifts.map { lift ->
            val renderedSets = lift.slots.mapNotNull { slot ->
                // Deleted slots are removed from the projection.
                if (slot.slotId in overlay.deletedSlotIds) return@mapNotNull null

                val baseline = snapshot.bySlot[slot.slotId] ?: overlay.dynamicBaselines[slot.slotId]
                val logged = overlay.loggedSets[slot.slotId]
                val recommendation = overlay.recommendations[slot.slotId]

                // recommendedWeight resolution (D1 merge priority for the display field):
                //  - if the slot has a logged value, the snapshot baseline is preserved for display
                //    (the logged value is the new ground truth; the recommendation is ignored);
                //  - if the slot is upcoming (no logged value) and a recommendation exists, the
                //    recommendation supersedes the snapshot baseline;
                //  - otherwise the snapshot baseline or overlay dynamic baseline is used.
                val recommendedWeight = when {
                    logged != null ->
                        baseline?.recommendedWeight
                            ?: recommendation
                            ?: Weight(0.0, logged.weight.unit)
                    recommendation != null -> recommendation
                    else -> baseline?.recommendedWeight ?: return@mapNotNull null
                }
                val recommendedReps = baseline?.baselineExpectedReps ?: 0

                RenderedSet(
                    slotId = slot.slotId,
                    workoutLiftId = slot.workoutLiftId,
                    liftId = slot.liftId,
                    position = slot.position,
                    setType = slot.setType,
                    isTopSet = slot.isTopSet,
                    repMin = slot.repMin,
                    repMax = slot.repMax,
                    rirGoal = slot.rirGoal,
                    restSeconds = slot.restSeconds,
                    recommendedWeight = recommendedWeight,
                    recommendedReps = recommendedReps,
                    loggedValue = logged,
                )
            }
            RenderedWorkoutLift(
                workoutLiftId = lift.workoutLiftId,
                liftId = lift.liftId,
                liftName = lift.liftName,
                progressionScheme = lift.progressionScheme,
                sets = renderedSets,
            )
        }
        return RenderedWorkout(
            workoutId = structure.workoutId,
            workoutName = structure.workoutName,
            lifts = lifts,
        )
    }
}
