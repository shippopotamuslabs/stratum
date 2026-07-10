package com.stratum.domain.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.identity.ProgramId
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
 * Persists edits to a single workout template by merging into a [Program] and replacing the full
 * hierarchy via [ProgramRepository.saveProgram].
 *
 * When [programId] is null, targets the **active** program (Programs / live edit path). When set,
 * loads that program (library or active) so inactive library workouts can be edited without
 * activating them first. Active flag on the loaded program is preserved.
 */
class SaveWorkoutUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        workoutId: WorkoutId,
        updatedWorkout: Workout,
        programId: ProgramId? = null,
    ): Result<Unit> {
        val program =
            if (programId != null) {
                programRepository.getProgramById(programId)
                    ?: return Result.failure(
                        IllegalStateException("Program ${programId.value} not found to save workout"),
                    )
            } else {
                programRepository.getProgram()
                    ?: return Result.failure(IllegalStateException("No active program found to save workout"))
            }
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
