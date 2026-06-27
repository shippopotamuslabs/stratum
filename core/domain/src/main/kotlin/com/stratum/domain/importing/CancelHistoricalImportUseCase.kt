package com.stratum.domain.importing

import javax.inject.Inject

class CancelHistoricalImportUseCase @Inject constructor(
    private val rollbackHistoricalImport: RollbackHistoricalImportUseCase,
    private val refreshPersistedImportState: RefreshPersistedImportStateUseCase,
) {
    suspend operator fun invoke(): RefreshPersistedImportStateResult {
        rollbackHistoricalImport()
        return refreshPersistedImportState()
    }
}
