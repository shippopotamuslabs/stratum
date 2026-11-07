package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Program

/**
 * Outcome of [UpdateExistingProgramUseCase] (STR-243).
 *
 * The validation branches are expected caller outcomes: they leave a warning
 * breadcrumb only, never a Crashlytics non-fatal, exactly as the former
 * `expectedFailure` path did. An operational save failure is logged once via
 * [LoggingPort.error] at the use-case boundary and surfaces as
 * [PersistenceFailed]. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface UpdateExistingProgramResult {
    data object Success : UpdateExistingProgramResult

    /** The program has no workouts at all. */
    data object NoWorkouts : UpdateExistingProgramResult

    /** The program has workouts but none of them contains an exercise. */
    data object NoExercises : UpdateExistingProgramResult

    /** The program save failed (repository / store I/O). */
    data object PersistenceFailed : UpdateExistingProgramResult
}

/**
 * Persists an existing program draft while **preserving** [Program.isActive] (and other fields on
 * the model). Unlike [SaveProgramToLibraryUseCase], does not force inactive.
 *
 * Callers that would drop the workout bound to an in-progress session must confirm with the user
 * and abandon that session **before** invoking this use case (see Create Program / Programs flows).
 */
class UpdateExistingProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(program: Program): UpdateExistingProgramResult {
        // Validation failures are expected caller outcomes, not operational faults: reject
        // before the logged persistence block so they never become Crashlytics non-fatals.
        if (program.workouts.isEmpty()) {
            loggingPort.warning(TAG, "Program must have at least one workout")
            return UpdateExistingProgramResult.NoWorkouts
        }
        if (program.workouts.none { workout -> workout.workoutLifts.isNotEmpty() }) {
            loggingPort.warning(TAG, "Program must have at least one exercise")
            return UpdateExistingProgramResult.NoExercises
        }
        return try {
            programRepository.saveProgram(program)
            UpdateExistingProgramResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to update program: ${error.message}", error)
            UpdateExistingProgramResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "UpdateExistingProgram"
    }
}
