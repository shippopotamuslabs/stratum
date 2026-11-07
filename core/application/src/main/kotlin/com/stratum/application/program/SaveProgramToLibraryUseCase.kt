package com.stratum.application.program

import com.stratum.application.diagnostics.OperationalDiagnostic
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
 * Outcome of [SaveProgramToLibraryUseCase] (STR-243; STR-237 Route (i)).
 *
 * The validation branches are expected caller outcomes and are not logged;
 * an operational save failure rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface SaveProgramToLibraryResult {
    data object Success : SaveProgramToLibraryResult

    /** The program has no workouts at all. */
    data object NoWorkouts : SaveProgramToLibraryResult

    /** The program has workouts but none of them contains an exercise. */
    data object NoExercises : SaveProgramToLibraryResult

    /** The library save failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : SaveProgramToLibraryResult
}

/**
 * Persists a program as inactive library inventory without changing the active training program.
 */
class SaveProgramToLibraryUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
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
            SaveProgramToLibraryResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to save program to library: ${error.message}",
                        cause = error,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "SaveProgramToLibrary"
    }
}
