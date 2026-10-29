package com.stratum.application.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.workout.ProgressionSnapshot
import com.stratum.domain.workout.RenderedSet
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.RenderedWorkoutLift
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.domain.workout.resolvedWorkoutPinnedNotes
import com.stratum.domain.workout.applyRestOverride

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
                // Prefer logged → live rec → snapshot/dynamic baseline. Overlay-generated slots
                // (myo minis, drops) may briefly lack a baseline; keep them visible with a zero
                // seed so recompute can attach real recs on the next pass.
                val recommendedWeight = when {
                    logged != null ->
                        baseline?.recommendedWeight
                            ?: recommendation?.weight
                            ?: Weight(0.0, logged.weight.unit)
                    recommendation != null -> recommendation.weight
                    baseline != null -> baseline.recommendedWeight
                    else -> Weight(0.0, Weight.WeightUnit.Lb)
                }
                // recommendedReps: a live overlay recommendation projects expected reps one
                // set-index forward from the most recent performed set (D4). When no live
                // recommendation exists, fall back to the snapshot/dynamic baseline. The
                // `hasLiveRepsRecommendation` flag tells the UI whether to render the
                // concrete projected count or the `repMin-repMax` range placeholder.
                val hasLiveRepsRecommendation =
                    recommendation != null && recommendation.reps > 0
                val recommendedReps = if (hasLiveRepsRecommendation) {
                    recommendation.reps
                } else {
                    baseline?.baselineExpectedReps ?: 0
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
                    restSeconds = slot.applyRestOverride(overlay).restSeconds,
                    recommendedWeight = recommendedWeight,
                    recommendedReps = recommendedReps,
                    hasLiveRepsRecommendation = hasLiveRepsRecommendation,
                    loggedValue = logged,
                    previousWeight = baseline?.previousWeight,
                    previousReps = baseline?.previousReps,
                    previousRir = baseline?.previousRir,
                    previousPartialReps = baseline?.previousPartialReps,
                    isProjected = baseline?.isProjected ?: false,
                    myoRepKind = slot.myoRepKind,
                )
            }
            RenderedWorkoutLift(
                workoutLiftId = lift.workoutLiftId,
                liftId = lift.liftId,
                liftName = lift.liftName,
                progressionScheme = lift.progressionScheme,
                sets = renderedSets,
                supersetGroupId = lift.supersetGroupId,
                // The feature merge resolves the live-swap override onto the projection
                // before this runs, so this copy carries the in-use equipment.
                equipmentId = lift.equipmentId,
                loadType = lift.loadType,
                useRestTimerBetweenLifts = lift.useRestTimerBetweenLifts,
                isPlateLoaded = lift.isPlateLoaded,
                isDoubleSided = lift.isDoubleSided,
                workoutPinnedNotes = resolvedWorkoutPinnedNotes(
                    overlay,
                    lift.workoutLiftId,
                    lift.workoutPinnedNotes,
                ),
                liftPinnedNotes = lift.liftPinnedNotes,
            )
        }
        val orderedLiftIds = overlay.orderedLiftIds
        val sortedLifts = if (orderedLiftIds != null) {
            lifts.sortedBy { lift ->
                val index = orderedLiftIds.indexOf(lift.workoutLiftId.value)
                if (index != -1) index else Int.MAX_VALUE
            }
        } else {
            lifts
        }
        return RenderedWorkout(
            workoutId = structure.workoutId,
            workoutName = structure.workoutName,
            lifts = sortedLifts,
        )
    }
}
