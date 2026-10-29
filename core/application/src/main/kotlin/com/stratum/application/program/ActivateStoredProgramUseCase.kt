package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.schedule.resolveInitialCycleStartEpochDay
import javax.inject.Inject
import com.stratum.domain.program.Program

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
    suspend operator fun invoke(programId: ProgramId): Result<Unit> {
        if (programId.isImportHistory) {
            return Result.failure(IllegalArgumentException("Import-history program cannot be activated"))
        }
        val existing =
            programRepository.getProgramById(programId)
                ?: return Result.failure(IllegalArgumentException("Cannot activate unknown program: ${programId.value}"))
        if (existing.workouts.isEmpty()) {
            return Result.failure(IllegalArgumentException("Program must have at least one workout"))
        }
        if (existing.workouts.none { it.workoutLifts.isNotEmpty() }) {
            return Result.failure(IllegalArgumentException("Program must have at least one exercise"))
        }
        val resolvedStart =
            existing.cycleStartEpochDay ?: resolveInitialCycleStartEpochDay(existing)
        return runCatchingLogged(loggingPort, TAG, "Failed to activate program") {
            if (existing.cycleStartEpochDay != resolvedStart || !existing.isActive) {
                programRepository.saveProgram(
                    existing.copy(cycleStartEpochDay = resolvedStart).activate(),
                )
            } else {
                programRepository.activateProgram(programId)
            }
        }
    }

    private companion object {
        const val TAG = "ActivateStoredProgram"
    }
}
