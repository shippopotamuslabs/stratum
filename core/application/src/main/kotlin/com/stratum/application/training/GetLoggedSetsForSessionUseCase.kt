package com.stratum.application.training

import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import com.stratum.domain.training.LoggedSet

class GetLoggedSetsForSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): List<LoggedSet> {
        return trainingLogRepository.getSetsForSession(sessionId)
    }
}
