package com.stratum.feature.settings

import com.stratum.domain.export.ExportLogPreview
import com.stratum.domain.importing.ImportFailureKind
import com.stratum.domain.importing.ImportFileValidationResult
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.MergeImportStats
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ImportPresentationMappingTest {

    @Test
    @DisplayName("Export preview maps session and set counts")
    fun exportPreviewToUi() {
        val ui = ExportLogPreview(sessionCount = 3, setCount = 40).toUi()
        assertEquals(3, ui.sessionCount)
        assertEquals(40, ui.setCount)
    }

    @Test
    @DisplayName("Merge stats map all four counters")
    fun mergeStatsToUi() {
        val ui =
            MergeImportStats(
                sessionsInserted = 1,
                setsInserted = 2,
                sessionsSkipped = 3,
                setsSkipped = 4,
            ).toUi()
        assertEquals(1, ui.sessionsInserted)
        assertEquals(3, ui.sessionsSkipped)
        assertEquals(2, ui.setsInserted)
        assertEquals(4, ui.setsSkipped)
    }

    @Test
    @DisplayName("Historical import timeline collapses domain phases onto four steps")
    fun historicalTimelineIndex() {
        assertEquals(0, ImportLogsPhaseUi.Uploading.historicalImportTimelineIndex())
        assertEquals(1, ImportLogsPhaseUi.ReadingData.historicalImportTimelineIndex())
        assertEquals(1, ImportLogsPhaseUi.CreatingConverter.historicalImportTimelineIndex())
        assertEquals(1, ImportLogsPhaseUi.ValidatingSample.historicalImportTimelineIndex())
        assertEquals(1, ImportLogsPhaseUi.ConvertingFull.historicalImportTimelineIndex())
        assertEquals(2, ImportLogsPhaseUi.EnrichingCustomLifts.historicalImportTimelineIndex())
        assertEquals(3, ImportLogsPhaseUi.ExtractingInsights.historicalImportTimelineIndex())
        assertEquals(
            HISTORICAL_TIMELINE_STEP_COUNT,
            ImportLogsPhaseUi.Complete.historicalImportTimelineIndex(),
        )
        assertEquals(0, ImportLogsPhaseUi.Failed.historicalImportTimelineIndex())
        assertEquals(0, (null as ImportLogsPhaseUi?).historicalImportTimelineIndex())
    }

    @Test
    @DisplayName("Domain ImportProgressPhase maps to presentation enum")
    fun progressPhaseToUi() {
        assertEquals(ImportLogsPhaseUi.ConvertingFull, ImportProgressPhase.ConvertingFull.toUi())
        assertEquals(ImportLogsPhaseUi.Complete, ImportProgressPhase.Complete.toUi())
        assertEquals(ImportLogsPhaseUi.Failed, ImportProgressPhase.Failed.toUi())
    }

    @Test
    @DisplayName("Program import timeline collapses intermediate phases onto Convert")
    fun programTimelineIndex() {
        assertEquals(0, ImportProgramPhaseUi.Uploading.programImportTimelineIndex())
        assertEquals(1, ImportProgramPhaseUi.Sniffing.programImportTimelineIndex())
        assertEquals(1, ImportProgramPhaseUi.Analyzing.programImportTimelineIndex())
        assertEquals(1, ImportProgramPhaseUi.Converting.programImportTimelineIndex())
        assertEquals(1, ImportProgramPhaseUi.Parsing.programImportTimelineIndex())
        assertEquals(2, ImportProgramPhaseUi.MatchingCatalog.programImportTimelineIndex())
        assertEquals(
            PROGRAM_TIMELINE_STEP_COUNT,
            ImportProgramPhaseUi.Complete.programImportTimelineIndex(),
        )
        assertEquals(0, ImportProgramPhaseUi.Failed.programImportTimelineIndex())
    }

    @Test
    @DisplayName("Domain ProgramTemplateImportPhase maps to presentation enum")
    fun programPhaseToUi() {
        assertEquals(
            ImportProgramPhaseUi.MatchingCatalog,
            ProgramTemplateImportPhase.MatchingCatalog.toUi(),
        )
        assertEquals(ImportProgramPhaseUi.Failed, ProgramTemplateImportPhase.Failed.toUi())
        assertEquals(ImportProgramPhaseUi.Complete, ProgramTemplateImportPhase.Complete.toUi())
    }

    @Test
    @DisplayName("Validation failures map to readable user messages")
    fun validationToUserMessage() {
        assertEquals(
            "No files selected.",
            ImportFileValidationResult.Invalid.NoFiles.toUserMessage(),
        )
        assertEquals(
            "Select at most 5 files.",
            ImportFileValidationResult.Invalid.TooManyFiles(5).toUserMessage(),
        )
        assertEquals(
            "big.csv exceeds the 20 MB limit.",
            ImportFileValidationResult.Invalid.FileTooLarge("big.csv", 20).toUserMessage(),
        )
    }

    @Test
    @DisplayName("Import failure kinds map to generic user-safe copy")
    fun failureKindToUserMessage() {
        assertEquals(IMPORT_LOGS_GENERIC_ERROR, ImportFailureKind.Generic.toUserMessage())
        assertEquals(
            IMPORT_LOGS_UNSUPPORTED_FILE_ERROR,
            ImportFailureKind.UnsupportedFile.toUserMessage(),
        )
    }
}
