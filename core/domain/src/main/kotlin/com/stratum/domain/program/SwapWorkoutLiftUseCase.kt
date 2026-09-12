package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Use case to swap the lift on a specific workout lift in the active program.
 * Modifies the template in memory and saves the updated program to persistence.
 */
class SwapWorkoutLiftUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(workoutLiftId: WorkoutLiftId, newLiftId: LiftId): Result<Unit> {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                loggingPort.error(TAG, "Failed to swap workout lift: ${error.message}", error)
                return Result.failure(error)
            }

        if (program == null) {
            val error = IllegalStateException("No active program found to swap lift")
            loggingPort.error(TAG, "Failed to swap workout lift: ${error.message}", error)
            return Result.failure(error)
        }

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
            val error = IllegalArgumentException("Workout lift with ID $workoutLiftId not found in the program")
            loggingPort.error(TAG, "Failed to swap workout lift: ${error.message}", error)
            return Result.failure(error)
        }

        val updatedProgram = program.copy(workouts = updatedWorkouts)
        return runCatchingLogged(loggingPort, TAG, "Failed to swap workout lift") {
            programRepository.saveProgram(updatedProgram)
            programChangeEventRepository.append(
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
        }
    }

    private companion object {
        const val TAG = "SwapWorkoutLift"
    }
}
