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
                            ?: recommendation?.weight
                            ?: Weight(0.0, logged.weight.unit)
                    recommendation != null -> recommendation.weight
                    else -> baseline?.recommendedWeight ?: return@mapNotNull null
                }
                // recommendedReps: a live overlay recommendation projects expected reps one
                // set-index forward from the most recent performed set (D4). When no live
                // recommendation exists, fall back to the snapshot/dynamic baseline. The
                // `hasLiveRepsRecommendation` flag tells the UI whether to render the
                // concrete projected count or the `repMin-repMax` range placeholder.
                val hasLiveRepsRecommendation =
                    recommendation != null && recommendation.reps > 0
                val recommendedReps = when {
                    hasLiveRepsRecommendation -> recommendation!!.reps
                    else -> baseline?.baselineExpectedReps ?: 0
                }

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
                    hasLiveRepsRecommendation = hasLiveRepsRecommendation,
                    loggedValue = logged,
                    previousWeight = baseline?.previousWeight,
                    previousReps = baseline?.previousReps,
                )
            }
            RenderedWorkoutLift(
                workoutLiftId = lift.workoutLiftId,
                liftId = lift.liftId,
                liftName = lift.liftName,
                progressionScheme = lift.progressionScheme,
                sets = renderedSets,
                supersetGroupId = lift.supersetGroupId,
                loadType = lift.loadType,
                useRestTimerBetweenLifts = lift.useRestTimerBetweenLifts,
            )
        }
        return RenderedWorkout(
            workoutId = structure.workoutId,
            workoutName = structure.workoutName,
            lifts = lifts,
        )
    }
}
