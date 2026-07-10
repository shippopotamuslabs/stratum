package com.stratum.domain.training

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Read-only observation of the in-progress training session (if any). */
class ObserveActiveSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    operator fun invoke(): Flow<TrainingSession?> = trainingLogRepository.observeActiveSession()
}
