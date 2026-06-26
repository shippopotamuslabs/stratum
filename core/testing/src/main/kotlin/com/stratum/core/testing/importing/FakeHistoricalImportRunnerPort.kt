package com.stratum.core.testing.importing

import com.stratum.domain.importing.ExtractTrainingInsightsSummary
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportRunnerPort
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
    val invocations = mutableListOf<Pair<ByteArray, String>>()

    fun setEvents(newEvents: List<HistoricalImportEvent>) {
        events = newEvents
    }

    override fun run(fileBytes: ByteArray, fileName: String): Flow<HistoricalImportEvent> = flow {
        invocations += fileBytes to fileName
        events.forEach { emit(it) }
    }
}
