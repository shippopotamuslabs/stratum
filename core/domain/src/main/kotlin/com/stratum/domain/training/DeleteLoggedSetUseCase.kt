package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import javax.inject.Inject

class DeleteLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(id: LoggedSetId) {
        trainingLogRepository.deleteSet(id)
    }
}
