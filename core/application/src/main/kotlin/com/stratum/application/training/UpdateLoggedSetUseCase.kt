package com.stratum.application.training

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.TrainingSessionStatus
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.training.LoggedSet

/**
 * Outcome of updating a logged set (STR-243).
 *
 * The session-guard branches carry the seam's typed facts ([SessionNotFound] /
 * [SessionNotInProgress]). The originating throwable of a failed write rides
 * the [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause); the presentation boundary drains
 * it exactly once through its module's DiagnosticDrain. The guard branches'
 * former error breadcrumbs are dropped: their facts are the branches
 * themselves (STR-237). Coroutine cancellation is not a branch: it
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
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : UpdateLoggedSetResult
}

/**
 * Updates an existing in-progress session logged set (weight / reps / RIR) in place.
 * Used when the lifter amends a completed set without unlogging it.
 */
class UpdateLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(set: LoggedSet): UpdateLoggedSetResult {
        // Single load via the seam check: a missing / non-live session is returned as a
        // typed branch (never thrown); an operational load failure is captured by the
        // seam callback as a diagnostic with the same legacy line the write envelope
        // below produces and returned on PersistenceFailed.
        // Cancellation from the load propagates as cancellation out of the check.
        var loadDiagnostic: OperationalDiagnostic? = null
        when (val check = trainingLogRepository.checkInProgressSession(set.sessionId) { error ->
            loadDiagnostic =
                OperationalDiagnostic(
                    tag = TAG,
                    message = "Failed to update logged set: ${error.message}",
                    cause = error,
                )
        }) {
            is InProgressSessionCheck.NotFound ->
                return UpdateLoggedSetResult.SessionNotFound(check.sessionId)
            is InProgressSessionCheck.NotInProgress ->
                return UpdateLoggedSetResult.SessionNotInProgress(check.sessionId, check.status)
            InProgressSessionCheck.LoadFailed ->
                return UpdateLoggedSetResult.PersistenceFailed(
                    diagnostics = listOf(checkNotNull(loadDiagnostic)),
                )
            is InProgressSessionCheck.Ready -> Unit
        }
        return try {
            trainingLogRepository.updateSet(set)
            UpdateLoggedSetResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            UpdateLoggedSetResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to update logged set: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "UpdateLoggedSetUseCase"
    }
}
