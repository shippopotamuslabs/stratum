package com.stratum.application.program

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.program.identity.ProgramId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Program

/**
 * Outcome of [DiscardActiveProgramUseCase] (STR-243; STR-237 Route (i)).
 *
 * The refusal branches mirror the [DeleteProgramUseCase] refusals for the
 * active program; an operational inspect failure rides this
 * [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause), and a delete failure's
 * diagnostics propagate unchanged from [DeleteProgramResult.PersistenceFailed].
 * The presentation boundary drains them exactly once through its module's
 * DiagnosticDrain. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface DiscardActiveProgramResult {
    data object Success : DiscardActiveProgramResult

    /** The active program is the import-history program, which cannot be deleted. */
    data class ImportHistoryProgram(val programId: ProgramId) : DiscardActiveProgramResult

    /** An in-progress session is linked to the active program. */
    data class SessionInProgress(val programId: ProgramId) : DiscardActiveProgramResult

    /** Inspecting or deleting the active program failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : DiscardActiveProgramResult
}

/**
 * Deletes the user's active program template if one exists (e.g. onboarding preview
 * "Discard Program"). No-op when there is no active program. History soft-refs are handled by
 * [DeleteProgramUseCase] / [ProgramRepository.deleteProgram].
 */
class DiscardActiveProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val deleteProgram: DeleteProgramUseCase,
) {
    suspend operator fun invoke(): DiscardActiveProgramResult {
        val active =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                return DiscardActiveProgramResult.PersistenceFailed(
                    diagnostics = listOf(
                        OperationalDiagnostic(
                            tag = TAG,
                            message = "Failed to inspect active program: ${error.message}",
                            cause = error,
                        ),
                    ),
                )
            }
                ?: return DiscardActiveProgramResult.Success

        return when (val deleteResult = deleteProgram(active.id)) {
            DeleteProgramResult.Success -> DiscardActiveProgramResult.Success
            is DeleteProgramResult.ImportHistoryProgram ->
                DiscardActiveProgramResult.ImportHistoryProgram(deleteResult.programId)
            is DeleteProgramResult.SessionInProgress ->
                DiscardActiveProgramResult.SessionInProgress(deleteResult.programId)
            is DeleteProgramResult.PersistenceFailed ->
                DiscardActiveProgramResult.PersistenceFailed(deleteResult.diagnostics)
        }
    }

    private companion object {
        const val TAG = "DiscardActiveProgram"
    }
}
