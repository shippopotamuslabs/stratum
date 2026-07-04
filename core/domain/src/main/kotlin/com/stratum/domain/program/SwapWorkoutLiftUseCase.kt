package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import javax.inject.Inject

/**
 * Use case to swap the lift on a specific workout lift in the active program.
 * Modifies the template in memory and saves the updated program to persistence.
 */
class SwapWorkoutLiftUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(workoutLiftId: WorkoutLiftId, newLiftId: LiftId): Result<Unit> {
        val program = programRepository.getProgram() ?: return Result.failure(
            IllegalStateException("No active program found to swap lift")
        )

        var found = false
        val updatedWorkouts = program.workouts.map { workout ->
            val updatedLifts = workout.workoutLifts.map { lift ->
                if (lift.id == workoutLiftId) {
                    found = true
                    lift.copy(liftId = newLiftId)
                } else {
                    lift
                }
            }
            workout.copy(workoutLifts = updatedLifts)
        }

        if (!found) {
            return Result.failure(
                IllegalArgumentException("Workout lift with ID $workoutLiftId not found in the program")
            )
        }

        val updatedProgram = program.copy(workouts = updatedWorkouts)
        programRepository.saveProgram(updatedProgram)
        return Result.success(Unit)
    }
}
