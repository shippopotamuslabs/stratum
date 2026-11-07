package com.stratum.application.training

import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionNotInProgressException
import com.stratum.domain.training.TrainingSessionStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetches [sessionId], throwing [IllegalArgumentException] when missing and
 * [TrainingSessionNotInProgressException] unless the session is `IN_PROGRESS`.
 * The guard itself lives on the model ([TrainingSession.requireInProgress], STR-166);
 * this stays as the fetch-and-guard seam shared by the in-progress-session write paths
 * (log/amend/rewrite) so the lookup and error taxonomy cannot drift between use cases.
 */
internal suspend fun TrainingLogRepository.requireInProgressSession(
    sessionId: TrainingSessionId,
): TrainingSession {
    val session =
        getSession(sessionId)
            ?: throw IllegalArgumentException("Training session ${sessionId.value} not found")
    return session.requireInProgress()
}

/**
 * Outcome of the non-throwing in-progress session check ([checkInProgressSession]),
 * converted in STR-243 C5: the rejection branches carry the typed facts (session id,
 * actual status) instead of the raw [Throwable] the check used to carry, so callers
 * return their area outcome branches directly. The operational load failure is the
 * one branch that cannot carry its throwable: it goes to the diagnostics channel
 * through the caller's `onLoadFailure` callback (the caller logs it once with its
 * own legacy tag / "Failed to …" line) and the check reports the payload-free
 * [LoadFailed] kind.
 */
internal sealed interface InProgressSessionCheck {
    data class Ready(val session: TrainingSession) : InProgressSessionCheck

    /** No session row exists for [sessionId]. */
    data class NotFound(val sessionId: TrainingSessionId) : InProgressSessionCheck

    /** The session exists but its [status] is not `IN_PROGRESS`. */
    data class NotInProgress(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : InProgressSessionCheck

    /**
     * The session load itself failed operationally. The originating throwable was
     * already handed to the caller's diagnostics callback; it never crosses as a
     * payload (STR-243).
     */
    data object LoadFailed : InProgressSessionCheck
}

/**
 * Non-throwing sibling of [requireInProgressSession]: loads the session exactly once
 * and produces [InProgressSessionCheck.Ready] for an `IN_PROGRESS` session,
 * [InProgressSessionCheck.NotFound] when missing, or
 * [InProgressSessionCheck.NotInProgress] carrying the id and actual status when not
 * `IN_PROGRESS`. An operational load failure (e.g. a Room throw from `getSession`) is
 * passed once to [onLoadFailure] — the caller's diagnostics channel, which logs it
 * with the caller's exact legacy line — and reported as
 * [InProgressSessionCheck.LoadFailed], so the caller returns its failure branch
 * instead of letting the failure escape the invoke boundary.
 * [CancellationException] from the load is never carried or reported to the
 * callback: it propagates as cancellation.
 */
internal suspend fun TrainingLogRepository.checkInProgressSession(
    sessionId: TrainingSessionId,
    onLoadFailure: (Throwable) -> Unit = {},
): InProgressSessionCheck {
    val session =
        try {
            getSession(sessionId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            onLoadFailure(error)
            return InProgressSessionCheck.LoadFailed
        }
    return when {
        session == null -> InProgressSessionCheck.NotFound(sessionId)
        session.status != TrainingSessionStatus.IN_PROGRESS ->
            InProgressSessionCheck.NotInProgress(session.id, session.status)
        else -> InProgressSessionCheck.Ready(session)
    }
}
