package com.stratum.domain.importing

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class RunHistoricalImportUseCase @Inject constructor(
    private val runner: HistoricalImportRunnerPort,
) {
    operator fun invoke(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
        persistPolicy: ImportHistoryPersistPolicy,
        fallbackWeightUnit: com.stratum.domain.lift.Weight.WeightUnit =
            com.stratum.domain.lift.Weight.WeightUnit.Lb,
    ): Flow<HistoricalImportEvent> =
        runner.run(
            files = files,
            resume = resume,
            persistPolicy = persistPolicy,
            fallbackWeightUnit = fallbackWeightUnit,
        )
}
