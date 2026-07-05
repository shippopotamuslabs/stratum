package com.stratum.domain.training

import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

class AbandonTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<Unit> = runCatching {
        trainingLogRepository.deleteSession(sessionId)
    }
}
