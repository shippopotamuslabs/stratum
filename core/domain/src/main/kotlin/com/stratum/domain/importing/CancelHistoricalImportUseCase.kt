package com.stratum.domain.importing

import javax.inject.Inject

/**
 * User-initiated stop / dismiss of a historical import attempt.
 *
 * Rolls back any partial local history **and** fails non-terminal local [ImportJob] rows so a
 * subsequent import allocates a new job id. Without failing local jobs, [StartHistoricalImportUseCase]
 * could reuse a stuck Pending/AgentRunning job and the remote `startImport` guard would report
 * "Another import is already in progress."
 */
class CancelHistoricalImportUseCase @Inject constructor(
    private val rollbackHistoricalImport: RollbackHistoricalImportUseCase,
    private val refreshPersistedImportState: RefreshPersistedImportStateUseCase,
    private val importJobRepository: ImportJobRepository,
) {
    suspend operator fun invoke(): RefreshPersistedImportStateResult {
        failActiveImportJobs(
            importJobRepository = importJobRepository,
            errorMessage = "Import cancelled.",
        )
        rollbackHistoricalImport()
        return refreshPersistedImportState()
    }
}
