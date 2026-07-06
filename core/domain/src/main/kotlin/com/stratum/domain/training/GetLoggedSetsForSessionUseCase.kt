package com.stratum.domain.training

import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

class GetLoggedSetsForSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): List<LoggedSet> {
        return trainingLogRepository.getSetsForSession(sessionId)
    }
}
