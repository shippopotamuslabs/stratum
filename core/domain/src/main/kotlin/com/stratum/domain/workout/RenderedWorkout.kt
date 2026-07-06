package com.stratum.domain.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId

/** Merge-result read model for a single set (D1). */
data class RenderedSet(
    val slotId: SetSlotId,
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val position: Int,
    val setType: SetType,
    val isTopSet: Boolean,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val restSeconds: Int,
    val recommendedWeight: Weight,
    val recommendedReps: Int,
    val loggedValue: LoggedSetValue?,
)

/** Merge-result read model for a single lift (D1). */
data class RenderedWorkoutLift(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val liftName: String,
    val progressionScheme: ProgressionScheme,
    val sets: List<RenderedSet>,
)

/** The fully merged rendered workout (D1). Pure output of `MergeRenderedWorkoutUseCase`. */
data class RenderedWorkout(
    val workoutId: WorkoutId,
    val workoutName: String,
    val lifts: List<RenderedWorkoutLift>,
)
