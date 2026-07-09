package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject

class DeleteLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LoggedSetId): Result<Unit> = runCatching {
        trainingLogRepository.deleteSet(id)
    }.onFailure { error ->
        loggingPort.error(TAG, "Failed to delete logged set: ${error.message}", error)
    }

    private companion object {
        const val TAG = "DeleteLoggedSetUseCase"
    }
}
