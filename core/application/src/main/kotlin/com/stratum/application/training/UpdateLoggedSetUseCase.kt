package com.stratum.application.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.TrainingSessionNotInProgressException
import com.stratum.domain.training.TrainingSessionStatus
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.training.LoggedSet

/**
 * Outcome of updating a logged set (STR-243).
 *
 * The session-guard branches carry the seam's typed facts ([SessionNotFound] /
 * [SessionNotInProgress]). The originating throwable of a failed write goes once
 * to the diagnostics channel ([LoggingPort.error]) at the use-case boundary; it
 * never crosses as a payload. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface UpdateLoggedSetResult {
    data object Success : UpdateLoggedSetResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : UpdateLoggedSetResult

    data class SessionNotInProgress(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : UpdateLoggedSetResult

    /** The set write failed (repository / store I/O). */
    data object PersistenceFailed : UpdateLoggedSetResult
}

/**
 * Updates an existing in-progress session logged set (weight / reps / RIR) in place.
 * Used when the lifter amends a completed set without unlogging it.
 */
class UpdateLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(set: LoggedSet): UpdateLoggedSetResult {
        // Single load via the seam check: a missing / non-live session is returned as a
        // typed branch (never thrown) and logged once with the same line the write
        // envelope below produces; an operational load failure is logged once by the
        // seam callback with that same line and returned as PersistenceFailed.
        // Cancellation from the load propagates as cancellation out of the check.
        when (val check = trainingLogRepository.checkInProgressSession(set.sessionId) { error ->
            loggingPort.error(TAG, "Failed to update logged set: ${error.message}", error)
        }) {
            is InProgressSessionCheck.NotFound -> {
                val error =
                    IllegalArgumentException("Training session ${check.sessionId.value} not found")
                loggingPort.error(TAG, "Failed to update logged set: ${error.message}", error)
                return UpdateLoggedSetResult.SessionNotFound(check.sessionId)
            }
            is InProgressSessionCheck.NotInProgress -> {
                val error = TrainingSessionNotInProgressException(check.sessionId, check.status)
                loggingPort.error(TAG, "Failed to update logged set: ${error.message}", error)
                return UpdateLoggedSetResult.SessionNotInProgress(check.sessionId, check.status)
            }
            InProgressSessionCheck.LoadFailed -> return UpdateLoggedSetResult.PersistenceFailed
            is InProgressSessionCheck.Ready -> Unit
        }
        return try {
            trainingLogRepository.updateSet(set)
            UpdateLoggedSetResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to update logged set: ${t.message}", t)
            UpdateLoggedSetResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "UpdateLoggedSetUseCase"
    }
}
