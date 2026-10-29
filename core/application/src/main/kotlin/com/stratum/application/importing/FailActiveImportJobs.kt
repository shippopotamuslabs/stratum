package com.stratum.application.importing

import com.stratum.domain.importing.ImportJobStatus
import java.time.Instant

/**
 * Marks every non-terminal local import job as [ImportJobStatus.Failed].
 *
 * Used when the user cancels/stops an import or starts a replacement so Room no longer reports an
 * "active" job that would block or be incorrectly reused by [StartHistoricalImportUseCase].
 */
internal suspend fun failActiveImportJobs(
    importJobRepository: ImportJobRepository,
    errorMessage: String,
    now: Instant = Instant.now(),
) {
    while (true) {
        val active = importJobRepository.getActiveJob() ?: break
        importJobRepository.updateJob(
            active.copy(
                status = ImportJobStatus.Failed,
                errorMessage = errorMessage,
                updatedAt = now,
            ),
        )
    }
}
