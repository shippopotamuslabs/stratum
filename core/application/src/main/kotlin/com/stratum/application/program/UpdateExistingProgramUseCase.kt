package com.stratum.application.program

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Program

/**
 * Outcome of [UpdateExistingProgramUseCase] (STR-243; STR-237 Route (i)).
 *
 * The validation branches are expected caller outcomes and are the record
 * the consumer acts on — their former warning breadcrumbs duplicated the
 * branch facts and are dropped. An operational save failure rides the
 * [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause); the presentation boundary
 * drains it exactly once through its module's DiagnosticDrain. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface UpdateExistingProgramResult {
    data object Success : UpdateExistingProgramResult

    /** The program has no workouts at all. */
    data object NoWorkouts : UpdateExistingProgramResult

    /** The program has workouts but none of them contains an exercise. */
    data object NoExercises : UpdateExistingProgramResult

    /** The program save failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : UpdateExistingProgramResult
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
) {
    suspend operator fun invoke(program: Program): UpdateExistingProgramResult {
        // Validation failures are expected caller outcomes, not operational faults: the
        // typed branches are their record, so they never become Crashlytics non-fatals.
        if (program.workouts.isEmpty()) {
            return UpdateExistingProgramResult.NoWorkouts
        }
        if (program.workouts.none { workout -> workout.workoutLifts.isNotEmpty() }) {
            return UpdateExistingProgramResult.NoExercises
        }
        return try {
            programRepository.saveProgram(program)
            UpdateExistingProgramResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            UpdateExistingProgramResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to update program: ${error.message}",
                        cause = error,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "UpdateExistingProgram"
    }
}
