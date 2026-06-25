package com.stratum.domain.lift

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveUsedHistoricalImportUseCase @Inject constructor(
    private val liftRepository: LiftRepository,
) {
    operator fun invoke(): Flow<Boolean> = liftRepository.observeUsedHistoricalImport()
}
