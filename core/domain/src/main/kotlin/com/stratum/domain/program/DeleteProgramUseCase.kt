package com.stratum.domain.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSessionStatus
import javax.inject.Inject

/**
 * Permanently removes a user **program template**. Training history (sessions + logged sets)
 * is retained; session soft refs are cleared and name snapshots remain for display.
 *
 * Refuses delete while an **in-progress** session is linked to this program so we never leave a
 * ghost active session (which would block new starts and force abandon → set CASCADE delete).
 * Import-history is refused.
 */
class DeleteProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(programId: ProgramId): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to delete program") {
            require(!programId.isImportHistory) {
                "Import-history program cannot be deleted"
            }
            val activeSession = trainingLogRepository.getActiveSession()
            if (
                activeSession != null &&
                activeSession.status == TrainingSessionStatus.IN_PROGRESS &&
                activeSession.programId == programId
            ) {
                error(
                    "Cannot delete this program while a workout is in progress. " +
                        "Finish or abandon the session first.",
                )
            }
            programRepository.deleteProgram(programId)
        }

    private companion object {
        const val TAG = "DeleteProgram"
    }
}
