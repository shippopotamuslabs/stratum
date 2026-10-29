package com.stratum.application.training

import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionNotInProgressException

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
