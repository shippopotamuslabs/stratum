package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
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

/**
 * Outcome of [SaveProgramToLibraryUseCase] (STR-243).
 *
 * The validation branches are expected caller outcomes and are not logged;
 * an operational save failure is logged once via [LoggingPort.error] at the
 * use-case boundary and surfaces as [PersistenceFailed]. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface SaveProgramToLibraryResult {
    data object Success : SaveProgramToLibraryResult

    /** The program has no workouts at all. */
    data object NoWorkouts : SaveProgramToLibraryResult

    /** The program has workouts but none of them contains an exercise. */
    data object NoExercises : SaveProgramToLibraryResult

    /** The library save failed (repository / store I/O). */
    data object PersistenceFailed : SaveProgramToLibraryResult
}

/**
 * Persists a program as inactive library inventory without changing the active training program.
 */
class SaveProgramToLibraryUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(program: Program): SaveProgramToLibraryResult {
        if (program.workouts.isEmpty()) return SaveProgramToLibraryResult.NoWorkouts
        if (program.workouts.none { it.workoutLifts.isNotEmpty() }) {
            return SaveProgramToLibraryResult.NoExercises
        }
        return try {
            val toSave = program.deactivate()
            programRepository.saveToLibrary(toSave)
            programChangeEventRepository.append(
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = toSave.id,
                    eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                    occurredAt = Instant.now(),
                    payload = ProgramChangeEventPayload.ProgramRegenerated,
                ),
            )
            SaveProgramToLibraryResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to save program to library: ${error.message}", error)
            SaveProgramToLibraryResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "SaveProgramToLibrary"
    }
}
