package com.stratum.domain.training

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject

/**
 * Updates an existing in-progress session logged set (weight / reps / RIR) in place.
 * Used when the lifter amends a completed set without unlogging it.
 */
class UpdateLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(set: LoggedSet): Result<Unit> = runCatching {
        val session =
            trainingLogRepository.getSession(set.sessionId)
                ?: throw IllegalArgumentException("Training session ${set.sessionId.value} not found")
        if (session.status != TrainingSessionStatus.IN_PROGRESS) {
            throw TrainingSessionNotInProgressException(set.sessionId, session.status)
        }
        trainingLogRepository.updateSet(set)
    }.onFailure { error ->
        loggingPort.error(TAG, "Failed to update logged set: ${error.message}", error)
    }

    private companion object {
        const val TAG = "UpdateLoggedSetUseCase"
    }
}
