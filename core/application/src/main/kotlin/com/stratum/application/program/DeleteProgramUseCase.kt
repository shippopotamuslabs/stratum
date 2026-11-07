package com.stratum.application.program

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.program.identity.ProgramId
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSessionStatus
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of [DeleteProgramUseCase] (STR-243; STR-237 Route (i)).
 *
 * The refusal branches are expected caller outcomes and are not logged; an
 * operational load/delete failure rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface DeleteProgramResult {
    data object Success : DeleteProgramResult

    /** Import-history programs can never be deleted. */
    data class ImportHistoryProgram(val programId: ProgramId) : DeleteProgramResult

    /** An in-progress session is linked to this program. */
    data class SessionInProgress(val programId: ProgramId) : DeleteProgramResult

    /** The delete failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : DeleteProgramResult
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
            DeleteProgramResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to delete program: ${error.message}",
                        cause = error,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "DeleteProgram"
    }
}
