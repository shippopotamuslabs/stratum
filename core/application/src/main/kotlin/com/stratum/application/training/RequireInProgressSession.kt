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
 * Outcome of the non-throwing in-progress session check ([checkInProgressSession]):
 * either the loaded session is ready for the write path, or the expected/operational
 * failure is carried as a value for the caller to return and log.
 */
internal sealed interface InProgressSessionCheck {
    data class Ready(val session: TrainingSession) : InProgressSessionCheck
    data class Rejected(val error: Throwable) : InProgressSessionCheck
}

/**
 * Non-throwing sibling of [requireInProgressSession]: loads the session exactly once
 * and produces [InProgressSessionCheck.Ready] for an `IN_PROGRESS` session, or
 * [InProgressSessionCheck.Rejected] carrying the same exception values the throwing
 * helper produces — [IllegalArgumentException] when missing,
 * [TrainingSessionNotInProgressException] when not `IN_PROGRESS`. An operational load
 * failure (e.g. a Room throw from `getSession`) is also carried as a [Rejected] value
 * so the caller can log it and return `Result.failure` instead of letting it escape
 * the invoke boundary. [CancellationException] from the load is never carried: it
 * propagates as cancellation.
 */
internal suspend fun TrainingLogRepository.checkInProgressSession(
    sessionId: TrainingSessionId,
): InProgressSessionCheck {
    val session =
        try {
            getSession(sessionId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return InProgressSessionCheck.Rejected(error)
        }
    return when {
        session == null ->
            InProgressSessionCheck.Rejected(
                IllegalArgumentException("Training session ${sessionId.value} not found"),
            )
        session.status != TrainingSessionStatus.IN_PROGRESS ->
            InProgressSessionCheck.Rejected(
                TrainingSessionNotInProgressException(session.id, session.status),
            )
        else -> InProgressSessionCheck.Ready(session)
    }
}
