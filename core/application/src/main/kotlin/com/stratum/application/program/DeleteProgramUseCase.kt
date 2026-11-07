package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.identity.ProgramId
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSessionStatus
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of [DeleteProgramUseCase] (STR-243).
 *
 * The refusal branches are expected caller outcomes and are not logged; an
 * operational load/delete failure is logged once via [LoggingPort.error] at
 * the use-case boundary and surfaces as [PersistenceFailed]. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface DeleteProgramResult {
    data object Success : DeleteProgramResult

    /** Import-history programs can never be deleted. */
    data class ImportHistoryProgram(val programId: ProgramId) : DeleteProgramResult

    /** An in-progress session is linked to this program. */
    data class SessionInProgress(val programId: ProgramId) : DeleteProgramResult

    /** The delete failed (repository / store I/O). */
    data object PersistenceFailed : DeleteProgramResult
}

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
    suspend operator fun invoke(programId: ProgramId): DeleteProgramResult {
        if (programId.isImportHistory) {
            return DeleteProgramResult.ImportHistoryProgram(programId)
        }
        return try {
            val activeSession = trainingLogRepository.getActiveSession()
            if (
                activeSession != null &&
                activeSession.status == TrainingSessionStatus.IN_PROGRESS &&
                activeSession.programId == programId
            ) {
                return DeleteProgramResult.SessionInProgress(programId)
            }
            programRepository.deleteProgram(programId)
            DeleteProgramResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to delete program: ${error.message}", error)
            DeleteProgramResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "DeleteProgram"
    }
}
