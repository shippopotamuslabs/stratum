package com.stratum.feature.settings

/**
 * Presentation models for settings import/export screens.
 * Domain types stay in ViewModels; composables consume only these.
 */

/** Preview counts for the export-logs range card. */
data class ExportLogPreviewUi(
    val sessionCount: Int,
    val setCount: Int,
)

/** Merge outcome after a successful settings historical import. */
data class MergeStatsUi(
    val sessionsInserted: Int,
    val sessionsSkipped: Int,
    val setsInserted: Int,
    val setsSkipped: Int,
)

/**
 * Coarse progress phases for historical log import.
 * Mirrors domain [com.stratum.domain.importing.ImportProgressPhase] without leaking it into UI.
 */
enum class ImportLogsPhaseUi {
    Uploading,
    ReadingData,
    CreatingConverter,
    ValidatingSample,
    ConvertingFull,
    EnrichingCustomLifts,
    ExtractingInsights,
    Complete,
    Failed,
}

data class ImportProgressUi(
    val phase: ImportLogsPhaseUi? = null,
    /** 1-based file progress during multi-file import. */
    val fileIndex: Int? = null,
    val fileCount: Int? = null,
)

/**
 * Coarse progress phases for program-template import (settings library path).
 * Mirrors domain ProgramTemplateImportPhase without leaking it into UI.
 */
enum class ImportProgramPhaseUi {
    Uploading,
    Sniffing,
    Analyzing,
    Converting,
    Parsing,
    MatchingCatalog,
    Complete,
    Failed,
}
