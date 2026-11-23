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

/**
 * One-shot navigation after a successful settings program import (STR-333).
 * Never picks one of several imported programs: many imports open the library.
 */
sealed interface ImportProgramNavigationEvent {
    /** Exactly one program imported: open its editor. */
    data class OpenProgramEditor(val programId: String) : ImportProgramNavigationEvent

    /** Several programs imported: open the library so the user can pick. */
    data object OpenProgramLibrary : ImportProgramNavigationEvent
}

/**
 * Navigation decision for a successful program import. Returns null when nothing was
 * imported (all skipped), so the user stays on the import screen with the result card.
 */
internal fun importProgramNavigationEventFor(
    importedProgramIds: List<String>,
): ImportProgramNavigationEvent? =
    when (importedProgramIds.size) {
        0 -> null
        1 -> ImportProgramNavigationEvent.OpenProgramEditor(importedProgramIds.single())
        else -> ImportProgramNavigationEvent.OpenProgramLibrary
    }
