package com.stratum.application.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

class AbandonTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<Unit> = runCatching {
        trainingLogRepository.deleteSession(sessionId)
    }.onFailure { error ->
        loggingPort.error(TAG, "Failed to abandon training session: ${error.message}", error)
    }

    private companion object {
        const val TAG = "AbandonTrainingSession"
    }
}
