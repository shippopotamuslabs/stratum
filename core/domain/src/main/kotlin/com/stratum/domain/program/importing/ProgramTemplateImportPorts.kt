package com.stratum.domain.program.importing


enum class ProgramTemplateImportPhase {
    Uploading,
    Sniffing,
    /** Server is normalizing the spreadsheet and scoring feasibility (custom path). */
    Analyzing,
    /** Server LLM is converting a custom spreadsheet into NormalizedProgramTemplate JSON. */
    Converting,
    Parsing,
    MatchingCatalog,
    Complete,
    Failed,
}

/** Server-side status of a program-template import job doc (`users/{uid}/programImportJobs/{id}`). */
enum class RemoteProgramTemplateJobStatus {
    Pending,
    Running,
    Ready,
    Rejected,
    Failed,
}

/**
 * Hard caps for program-template import. Program files are tiny structured documents (real-world
 * samples are single-digit KB), so this is deliberately far tighter than the historical-import
 * limit. Enforced client-side (before upload) and server-side (before parsing) so a direct
 * Callable invocation cannot bypass it.
 */

object ProgramTemplateImportLimits {
    const val MAX_FILE_BYTES = 1 * 1024 * 1024

    const val MAX_FILE_SIZE_MB = 1
}

/**
 * Client-facing snapshot of a program-template job, returned by [ProgramTemplateImportPort.pollJobStatus].
 *
 * [programTemplateStoragePath] is non-null once the job reaches [RemoteProgramTemplateJobStatus.Ready];
 * the client downloads the normalized template JSON from it via [ProgramTemplateImportPort.downloadProgramTemplate].
 */

data class ProgramTemplateJobSnapshot(
    val status: RemoteProgramTemplateJobStatus,
    val progressPhase: ProgramTemplateImportPhase?,
    val progressMessage: String?,
    val programTemplateStoragePath: String?,
    val errorMessage: String?,
)

/**
 * Cloud-side port for ingesting a program-template file (CSV/XLSX) and producing a normalized
 * [ProgramTemplate]. Mirrors [com.stratum.application.importing.HistoricalImportPort] but targets the
 * program-template job collection and Storage prefix.
 *
 * Implementations live in `:core:inference` (Firebase Callable + Storage client). The domain layer
 * never touches Firebase types directly (ARCHITECTURE.md §4.1).
 */
