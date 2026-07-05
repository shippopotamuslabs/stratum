package com.stratum.domain.program

import com.stratum.domain.program.identity.WorkoutId

/**
 * Replaces a single workout inside [Program.workouts], preserving all other workouts unchanged.
 */
fun Program.mergeWorkout(workoutId: WorkoutId, updatedWorkout: Workout): Program {
    var found = false
    val workouts =
        this.workouts.map { workout ->
            if (workout.id == workoutId) {
                found = true
                updatedWorkout.copy(programId = id)
            } else {
                workout
            }
        }
    require(found) { "Workout with ID $workoutId not found in program" }
    return copy(workouts = workouts)
}
