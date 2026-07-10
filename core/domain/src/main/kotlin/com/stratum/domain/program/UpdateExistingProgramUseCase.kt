package com.stratum.domain.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Persists an existing program draft while **preserving** [Program.isActive] (and other fields on
 * the model). Unlike [SaveProgramToLibraryUseCase], does not force inactive.
 */
class UpdateExistingProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(program: Program): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to update program") {
            require(program.workouts.isNotEmpty()) { "Program must have at least one workout" }
            require(program.workouts.any { workout -> workout.workoutLifts.isNotEmpty() }) {
                "Program must have at least one exercise"
            }
            programRepository.saveProgram(program)
        }

    private companion object {
        const val TAG = "UpdateExistingProgram"
    }
}
