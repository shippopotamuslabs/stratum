package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to swap the lift on a specific workout lift in the active program.
 * Modifies the template in memory and saves the updated program to persistence.
 */
class SwapWorkoutLiftUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository? = null,
) {
    suspend operator fun invoke(workoutLiftId: WorkoutLiftId, newLiftId: LiftId): Result<Unit> {
        val program = programRepository.getProgram() ?: return Result.failure(
            IllegalStateException("No active program found to swap lift")
        )

        var found = false
        var previousLiftId: LiftId? = null
        val updatedWorkouts = program.workouts.map { workout ->
            val updatedLifts = workout.workoutLifts.map { lift ->
                if (lift.id == workoutLiftId) {
                    found = true
                    previousLiftId = lift.liftId
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
        programChangeEventRepository?.append(
            ProgramChangeEvent(
                id = ProgramChangeEventId(UUID.randomUUID().toString()),
                programId = program.id,
                eventType = ProgramChangeEventType.LIFT_SWAPPED,
                occurredAt = Instant.now(),
                payload =
                    ProgramChangeEventPayload.LiftSwapped(
                        workoutLiftId = workoutLiftId,
                        previousLiftId = requireNotNull(previousLiftId),
                        newLiftId = newLiftId,
                    ),
            ),
        )
        return Result.success(Unit)
    }
}
