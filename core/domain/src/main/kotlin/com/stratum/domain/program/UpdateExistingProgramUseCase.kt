package com.stratum.domain.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.isOrphanedAgainstActiveProgram
import javax.inject.Inject

/**
 * Persists an existing program draft while **preserving** [Program.isActive] (and other fields on
 * the model). Unlike [SaveProgramToLibraryUseCase], does not force inactive.
 *
 * When a rewrite drops the workout bound to an in-progress session (mid-session delete), the
 * orphaned active session is abandoned so [com.stratum.domain.training.StartTrainingSessionUseCase]
 * is not permanently blocked.
 */
class UpdateExistingProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(program: Program): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to update program") {
            require(program.workouts.isNotEmpty()) { "Program must have at least one workout" }
            require(program.workouts.any { workout -> workout.workoutLifts.isNotEmpty() }) {
                "Program must have at least one exercise"
            }
            programRepository.saveProgram(program)
            abandonOrphanedActiveSession()
        }

    private suspend fun abandonOrphanedActiveSession() {
        val activeSession = trainingLogRepository.getActiveSession() ?: return
        val activeProgram = programRepository.getProgram()
        if (activeSession.isOrphanedAgainstActiveProgram(activeProgram)) {
            trainingLogRepository.deleteSession(activeSession.id)
        }
    }

    private companion object {
        const val TAG = "UpdateExistingProgram"
    }
}
