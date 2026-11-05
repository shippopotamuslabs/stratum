package com.stratum.application.training

import com.stratum.domain.coroutines.runCatchingCancellable
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSessionNotInProgressException
import com.stratum.domain.training.TrainingSessionStatus

/**
 * Updates an existing in-progress session logged set (weight / reps / RIR) in place.
 * Used when the lifter amends a completed set without unlogging it.
 */
class UpdateLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(set: LoggedSet): Result<Unit> {
        // Pre-check status after load: a non-live session is an expected failure, returned
        // as a constructed value (never thrown). The helper call below stays as the backstop.
        val loadedSession = trainingLogRepository.getSession(set.sessionId)
        if (loadedSession != null && loadedSession.status != TrainingSessionStatus.IN_PROGRESS) {
            val error = TrainingSessionNotInProgressException(loadedSession.id, loadedSession.status)
            loggingPort.error(TAG, "Failed to update logged set: ${error.message}", error)
            return Result.failure(error)
        }
        return runCatchingCancellable {
            trainingLogRepository.requireInProgressSession(set.sessionId)
            trainingLogRepository.updateSet(set)
        }.onFailure { error ->
            loggingPort.error(TAG, "Failed to update logged set: ${error.message}", error)
        }
    }

    private companion object {
        const val TAG = "UpdateLoggedSetUseCase"
    }
}
