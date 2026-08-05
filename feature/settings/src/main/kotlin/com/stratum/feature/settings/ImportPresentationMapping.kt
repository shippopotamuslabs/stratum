package com.stratum.feature.settings

import com.stratum.domain.export.ExportLogPreview
import com.stratum.domain.importing.ImportFailureKind
import com.stratum.domain.importing.ImportFileValidationResult
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.MergeImportStats
import com.stratum.domain.program.importing.ProgramTemplateImportPhase

// ── Domain → presentation ──────────────────────────────────────────────────

internal fun ExportLogPreview.toUi(): ExportLogPreviewUi =
    ExportLogPreviewUi(sessionCount = sessionCount, setCount = setCount)

internal fun MergeImportStats.toUi(): MergeStatsUi =
    MergeStatsUi(
        sessionsInserted = sessionsInserted,
        sessionsSkipped = sessionsSkipped,
        setsInserted = setsInserted,
        setsSkipped = setsSkipped,
    )

internal fun ImportProgress.toUi(): ImportProgressUi =
    ImportProgressUi(
        phase = phase.toUi(),
        fileIndex = fileIndex,
        fileCount = fileCount,
    )

internal fun ImportProgressPhase.toUi(): ImportLogsPhaseUi =
    when (this) {
        ImportProgressPhase.Uploading -> ImportLogsPhaseUi.Uploading
        ImportProgressPhase.ReadingData -> ImportLogsPhaseUi.ReadingData
        ImportProgressPhase.CreatingConverter -> ImportLogsPhaseUi.CreatingConverter
        ImportProgressPhase.ValidatingSample -> ImportLogsPhaseUi.ValidatingSample
        ImportProgressPhase.ConvertingFull -> ImportLogsPhaseUi.ConvertingFull
        ImportProgressPhase.EnrichingCustomLifts -> ImportLogsPhaseUi.EnrichingCustomLifts
        ImportProgressPhase.ExtractingInsights -> ImportLogsPhaseUi.ExtractingInsights
        ImportProgressPhase.Complete -> ImportLogsPhaseUi.Complete
        ImportProgressPhase.Failed -> ImportLogsPhaseUi.Failed
    }

/**
 * Map domain program-import phase to presentation.
 * Failed collapses to [ImportProgramPhaseUi.Failed] (settings shows error card, not a timeline step).
 */
internal fun ProgramTemplateImportPhase.toUi(): ImportProgramPhaseUi =
    when (this) {
        ProgramTemplateImportPhase.Uploading -> ImportProgramPhaseUi.Uploading
        ProgramTemplateImportPhase.Sniffing -> ImportProgramPhaseUi.Sniffing
        ProgramTemplateImportPhase.Analyzing -> ImportProgramPhaseUi.Analyzing
        ProgramTemplateImportPhase.Converting -> ImportProgramPhaseUi.Converting
        ProgramTemplateImportPhase.Parsing -> ImportProgramPhaseUi.Parsing
        ProgramTemplateImportPhase.MatchingCatalog -> ImportProgramPhaseUi.MatchingCatalog
        ProgramTemplateImportPhase.Complete -> ImportProgramPhaseUi.Complete
        ProgramTemplateImportPhase.Failed -> ImportProgramPhaseUi.Failed
    }

// ── User-facing copy (domain-free strings for VM errorMessage) ─────────────
// Technical / provider diagnostics stay in logs only — never put in UI state.

/** Log tag for historical import failures surfaced from [ImportLogsViewModel]. */
internal const val IMPORT_LOGS_LOG_TAG = "ImportLogs"

/** Generic infra/agent failure — never include server/provider text. */
internal const val IMPORT_LOGS_GENERIC_ERROR =
    "Something went wrong importing your logs. Please try again."

/** Server rejected the file as not a training log. */
internal const val IMPORT_LOGS_UNSUPPORTED_FILE_ERROR =
    "This file doesn't look like a training log. Try a different export."

/** SAF / local file materialization failure. */
internal const val IMPORT_LOGS_READ_FILES_ERROR = "Could not read the selected files."

internal fun ImportFileValidationResult.Invalid.toUserMessage(): String =
    when (this) {
        ImportFileValidationResult.Invalid.NoFiles -> "No files selected."
        is ImportFileValidationResult.Invalid.TooManyFiles -> "Select at most $max files."
        is ImportFileValidationResult.Invalid.FileTooLarge ->
            "$fileName exceeds the $maxMb MB limit."
    }

/** Maps [ImportFailureKind] to safe UI copy. Technical [message] must be logged separately. */
internal fun ImportFailureKind.toUserMessage(): String =
    when (this) {
        ImportFailureKind.UnsupportedFile -> IMPORT_LOGS_UNSUPPORTED_FILE_ERROR
        ImportFailureKind.Generic -> IMPORT_LOGS_GENERIC_ERROR
    }

// ── Timeline indices (shared by VMs + screens; presentation types only) ────

/** Four-step timeline: Upload → Convert → Catalog → Analyze. */
internal const val HISTORICAL_TIMELINE_STEP_COUNT = 4

/**
 * Index into the settings historical-import timeline labels.
 * [ImportLogsPhaseUi.Complete] returns [HISTORICAL_TIMELINE_STEP_COUNT] (all done).
 */
internal fun ImportLogsPhaseUi?.historicalImportTimelineIndex(): Int =
    when (this) {
        null -> 0
        ImportLogsPhaseUi.Uploading -> 0
        ImportLogsPhaseUi.ReadingData,
        ImportLogsPhaseUi.CreatingConverter,
        ImportLogsPhaseUi.ValidatingSample,
        ImportLogsPhaseUi.ConvertingFull,
        -> 1
        ImportLogsPhaseUi.EnrichingCustomLifts -> 2
        ImportLogsPhaseUi.ExtractingInsights -> 3
        ImportLogsPhaseUi.Complete -> HISTORICAL_TIMELINE_STEP_COUNT
        ImportLogsPhaseUi.Failed -> 0
    }

/** Four-step timeline: Upload → Convert → Match → Done. */
internal const val PROGRAM_TIMELINE_STEP_COUNT = 4

/**
 * Index into the settings program-import timeline labels.
 * Intermediate domain phases collapse onto Convert (index 1).
 * [ImportProgramPhaseUi.Complete] returns [PROGRAM_TIMELINE_STEP_COUNT] (all done).
 */
internal fun ImportProgramPhaseUi?.programImportTimelineIndex(): Int =
    when (this) {
        null -> 0
        ImportProgramPhaseUi.Uploading -> 0
        ImportProgramPhaseUi.Sniffing,
        ImportProgramPhaseUi.Analyzing,
        ImportProgramPhaseUi.Converting,
        ImportProgramPhaseUi.Parsing,
        -> 1
        ImportProgramPhaseUi.MatchingCatalog -> 2
        ImportProgramPhaseUi.Complete -> PROGRAM_TIMELINE_STEP_COUNT
        ImportProgramPhaseUi.Failed -> 0
    }
