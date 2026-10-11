package com.stratum.domain.training

import com.stratum.domain.training.identity.TrainingSessionId

/**
 * Fetches [sessionId], throwing [IllegalArgumentException] when missing and
 * [TrainingSessionNotInProgressException] unless the session is `IN_PROGRESS`.
 * Shared by the in-progress-session write paths (log/amend/rewrite) so the guard
 * and error taxonomy cannot drift between use cases.
 */
internal suspend fun TrainingLogRepository.requireInProgressSession(
    sessionId: TrainingSessionId,
): TrainingSession {
    val session =
        getSession(sessionId)
            ?: throw IllegalArgumentException("Training session ${sessionId.value} not found")
    if (session.status != TrainingSessionStatus.IN_PROGRESS) {
        throw TrainingSessionNotInProgressException(sessionId, session.status)
    }
    return session
}
