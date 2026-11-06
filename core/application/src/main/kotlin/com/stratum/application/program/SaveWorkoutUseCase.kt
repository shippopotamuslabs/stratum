package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.application.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.mergeWorkout

/**
 * Outcome of [SaveWorkoutUseCase] (STR-243).
 *
 * The not-found branches are expected caller outcomes and are not logged; an
 * operational load/save failure is logged once via [LoggingPort.error] at the
 * use-case boundary and surfaces as [PersistenceFailed]. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface SaveWorkoutResult {
    data object Success : SaveWorkoutResult

    /** No active program exists (only reachable when no explicit program id is given). */
    data object NoActiveProgram : SaveWorkoutResult

    /** No program exists for the explicitly requested id. */
    data class ProgramNotFound(val programId: ProgramId) : SaveWorkoutResult

    /** The target workout is not part of the loaded program. */
    data class WorkoutNotFound(val workoutId: WorkoutId) : SaveWorkoutResult

    /** The save failed (repository / store I/O). */
    data object PersistenceFailed : SaveWorkoutResult
}

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
    ): SaveWorkoutResult {
        val program =
            try {
                if (programId != null) {
                    programRepository.getProgramById(programId)
                        ?: return SaveWorkoutResult.ProgramNotFound(programId)
                } else {
                    programRepository.getProgram()
                        ?: return SaveWorkoutResult.NoActiveProgram
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                loggingPort.error(TAG, "Failed to save workout: ${error.message}", error)
                return SaveWorkoutResult.PersistenceFailed
            }
        if (program.workouts.none { it.id == workoutId }) {
            return SaveWorkoutResult.WorkoutNotFound(workoutId)
        }
        return try {
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
            SaveWorkoutResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to save workout: ${error.message}", error)
            SaveWorkoutResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "SaveWorkout"
    }
}
