package com.stratum.application.importing

import javax.inject.Inject
import com.stratum.domain.importing.HistoricalImportSummary

data class RefreshPersistedImportStateResult(
    val hasCompletedHistoricalImport: Boolean,
    val importInsightsSummary: HistoricalImportSummary?,
)

class RefreshPersistedImportStateUseCase @Inject constructor(
    private val reconcileHistoricalImport: ReconcileHistoricalImportUseCase,
    private val loadHistoricalImportSummary: LoadHistoricalImportSummaryUseCase,
) {
    suspend operator fun invoke(): RefreshPersistedImportStateResult {
        val hasCompleted = reconcileHistoricalImport()
        val summary = if (hasCompleted) loadHistoricalImportSummary() else null
        return RefreshPersistedImportStateResult(
            hasCompletedHistoricalImport = hasCompleted,
            importInsightsSummary = summary,
        )
    }
}
