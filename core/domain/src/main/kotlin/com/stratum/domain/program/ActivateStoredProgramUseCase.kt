package com.stratum.domain.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.identity.ProgramId
import javax.inject.Inject

/**
 * Promotes an already-persisted library (or inactive) program to the sole active training program
 * without re-saving the full hierarchy. Prior active becomes library inventory.
 */
class ActivateStoredProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(programId: ProgramId): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to activate program") {
            require(!programId.isImportHistory) {
                "Import-history program cannot be activated"
            }
            val existing =
                programRepository.getProgramById(programId)
                    ?: error("Cannot activate unknown program: ${programId.value}")
            require(existing.workouts.isNotEmpty()) { "Program must have at least one workout" }
            require(existing.workouts.any { workout -> workout.workoutLifts.isNotEmpty() }) {
                "Program must have at least one exercise"
            }
            programRepository.activateProgram(programId)
        }

    private companion object {
        const val TAG = "ActivateStoredProgram"
    }
}
