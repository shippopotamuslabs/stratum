package com.stratum.domain.importing

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class RunHistoricalImportUseCase @Inject constructor(
    private val runner: HistoricalImportRunnerPort,
) {
    operator fun invoke(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
    ): Flow<HistoricalImportEvent> = runner.run(files, resume)
}
