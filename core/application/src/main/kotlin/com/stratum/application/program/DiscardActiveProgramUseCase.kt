package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.identity.ProgramId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Program

/**
 * Outcome of [DiscardActiveProgramUseCase] (STR-243).
 *
 * The refusal branches mirror the [DeleteProgramUseCase] refusals for the
 * active program; an operational inspect failure is logged once via
 * [LoggingPort.error] at this use-case boundary (a delete failure is logged
 * once inside [DeleteProgramUseCase]) and surfaces as [PersistenceFailed].
 * Coroutine cancellation is not a branch: it propagates.
 */
sealed interface DiscardActiveProgramResult {
    data object Success : DiscardActiveProgramResult

    /** The active program is the import-history program, which cannot be deleted. */
    data class ImportHistoryProgram(val programId: ProgramId) : DiscardActiveProgramResult

    /** An in-progress session is linked to the active program. */
    data class SessionInProgress(val programId: ProgramId) : DiscardActiveProgramResult

    /** Inspecting or deleting the active program failed (repository / store I/O). */
    data object PersistenceFailed : DiscardActiveProgramResult
}

/**
 * Deletes the user's active program template if one exists (e.g. onboarding preview
 * "Discard Program"). No-op when there is no active program. History soft-refs are handled by
 * [DeleteProgramUseCase] / [ProgramRepository.deleteProgram].
 */
class DiscardActiveProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val deleteProgram: DeleteProgramUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): DiscardActiveProgramResult {
        val active =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                loggingPort.error(TAG, "Failed to inspect active program: ${error.message}", error)
                return DiscardActiveProgramResult.PersistenceFailed
            }
                ?: return DiscardActiveProgramResult.Success

        return when (val deleteResult = deleteProgram(active.id)) {
            DeleteProgramResult.Success -> DiscardActiveProgramResult.Success
            is DeleteProgramResult.ImportHistoryProgram ->
                DiscardActiveProgramResult.ImportHistoryProgram(deleteResult.programId)
            is DeleteProgramResult.SessionInProgress ->
                DiscardActiveProgramResult.SessionInProgress(deleteResult.programId)
            DeleteProgramResult.PersistenceFailed -> DiscardActiveProgramResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "DiscardActiveProgram"
    }
}
