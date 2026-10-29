package com.stratum.application.training

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.training.TrainingSession

/** Read-only observation of the in-progress training session (if any). */
class ObserveActiveSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    operator fun invoke(): Flow<TrainingSession?> = trainingLogRepository.observeActiveSession()
}
