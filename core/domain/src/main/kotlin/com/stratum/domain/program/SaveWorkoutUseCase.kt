package com.stratum.domain.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Persists edits to a single workout template by merging into the active [Program] and
 * replacing the full hierarchy via [ProgramRepository.saveProgram].
 */
class SaveWorkoutUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(workoutId: WorkoutId, updatedWorkout: Workout): Result<Unit> {
        val program =
            programRepository.getProgram()
                ?: return Result.failure(IllegalStateException("No active program found to save workout"))
        return runCatchingLogged(loggingPort, TAG, "Failed to save workout") {
            val merged = program.mergeWorkout(workoutId, updatedWorkout)
            programRepository.saveProgram(merged)
            programChangeEventRepository.append(
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = program.id,
                    eventType = ProgramChangeEventType.WORKOUT_EDITED,
                    occurredAt = Instant.now(),
                    payload = ProgramChangeEventPayload.WorkoutEdited(workoutId),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "SaveWorkout"
    }
}
