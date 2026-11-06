package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.schedule.resolveInitialCycleStartEpochDay
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Program

/**
 * Outcome of [ActivateStoredProgramUseCase] (STR-243).
 *
 * The refusal branches are expected caller outcomes and are not logged; an
 * operational load/save failure is logged once via [LoggingPort.error] at the
 * use-case boundary and surfaces as [PersistenceFailed]. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface ActivateStoredProgramResult {
    data object Success : ActivateStoredProgramResult

    /** Import-history programs can never be activated. */
    data class ImportHistoryProgram(val programId: ProgramId) : ActivateStoredProgramResult

    /** No program exists for the requested id. */
    data class ProgramNotFound(val programId: ProgramId) : ActivateStoredProgramResult

    /** The program has no workouts at all. */
    data object NoWorkouts : ActivateStoredProgramResult

    /** The program has workouts but none of them contains an exercise. */
    data object NoExercises : ActivateStoredProgramResult

    /** The activation load/save failed (repository / store I/O). */
    data object PersistenceFailed : ActivateStoredProgramResult
}

/**
 * Promotes an already-persisted library (or inactive) program to the sole active training program
 * without re-saving the full hierarchy. Prior active becomes library inventory.
 *
 * Sets [Program.cycleStartEpochDay] only when missing (first activate). Existing anchors are kept
 * so mid-cycle deactivate/reactivate does not rewrite projection / rest weekday matching.
 * First-time 7-day anchors still use Monday of the week containing today
 * ([resolveInitialCycleStartEpochDay]).
 */
class ActivateStoredProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(programId: ProgramId): ActivateStoredProgramResult =
        try {
            doInvoke(programId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to activate program: ${error.message}", error)
            ActivateStoredProgramResult.PersistenceFailed
        }

    private suspend fun doInvoke(programId: ProgramId): ActivateStoredProgramResult {
        if (programId.isImportHistory) {
            return ActivateStoredProgramResult.ImportHistoryProgram(programId)
        }
        val existing =
            programRepository.getProgramById(programId)
                ?: return ActivateStoredProgramResult.ProgramNotFound(programId)
        if (existing.workouts.isEmpty()) {
            return ActivateStoredProgramResult.NoWorkouts
        }
        if (existing.workouts.none { it.workoutLifts.isNotEmpty() }) {
            return ActivateStoredProgramResult.NoExercises
        }
        val resolvedStart =
            existing.cycleStartEpochDay ?: resolveInitialCycleStartEpochDay(existing)
        if (existing.cycleStartEpochDay != resolvedStart || !existing.isActive) {
            programRepository.saveProgram(
                existing.copy(cycleStartEpochDay = resolvedStart).activate(),
            )
        } else {
            programRepository.activateProgram(programId)
        }
        return ActivateStoredProgramResult.Success
    }

    private companion object {
        const val TAG = "ActivateStoredProgram"
    }
}
