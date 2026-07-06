package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Workout

/**
 * Pure structure projection use case (D1). Builds the [WorkoutStructureProjection]
 * from the template [Workout] (no load). Slice 3 extends this to fold in overlay
 * structural mutations.
 */
class ProjectWorkoutStructureUseCase @javax.inject.Inject constructor() {
    operator fun invoke(
        workout: Workout,
        liftNames: Map<LiftId, String>,
    ): WorkoutStructureProjection {
        val lifts = workout.workoutLifts.map { workoutLift ->
            WorkoutLiftProjection(
                workoutLiftId = workoutLift.id,
                liftId = workoutLift.liftId,
                liftName = liftNames[workoutLift.liftId].orEmpty(),
                progressionScheme = workoutLift.progressionScheme,
                supersetGroupId = workoutLift.supersetGroupId,
                slots = workoutLift.sets.map { liftSet ->
                    SetSlot(
                        slotId = SetSlotId.Template(liftSet.id),
                        workoutLiftId = workoutLift.id,
                        liftId = workoutLift.liftId,
                        position = liftSet.position,
                        setType = liftSet.setType,
                        isTopSet = liftSet.isTopSet,
                        repMin = liftSet.repMin,
                        repMax = liftSet.repMax,
                        rirGoal = liftSet.rirGoal,
                        restSeconds = liftSet.restSeconds,
                    )
                },
            )
        }
        return WorkoutStructureProjection(
            workoutId = workout.id,
            workoutName = workout.name,
            lifts = lifts,
        )
    }
}
