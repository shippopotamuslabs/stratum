package com.stratum.core.testing.importing

import com.stratum.domain.importing.ExtractTrainingInsightsSummary
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportRunnerPort
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeHistoricalImportRunnerPort(
    private var events: List<HistoricalImportEvent> = listOf(
        HistoricalImportEvent.Progress(ImportProgress(ImportProgressPhase.Uploading)),
        HistoricalImportEvent.Completed(
            ExtractTrainingInsightsSummary(
                liftsWithInsights = 1,
                supersetPairs = 0,
                musclesWithBaselines = 1,
            ),
        ),
    ),
) : HistoricalImportRunnerPort {
    val invocations = mutableListOf<ImportInvocation>()

    data class ImportInvocation(
        val files: List<ImportFileInput>,
        val resume: HistoricalImportResumeState?,
        val persistPolicy: ImportHistoryPersistPolicy,
    )

    fun setEvents(newEvents: List<HistoricalImportEvent>) {
        events = newEvents
    }

    fun resetInvocations() {
        invocations.clear()
    }

    override fun run(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
        persistPolicy: ImportHistoryPersistPolicy,
    ): Flow<HistoricalImportEvent> = flow {
        invocations += ImportInvocation(files, resume, persistPolicy)
        events.forEach { emit(it) }
    }
}
