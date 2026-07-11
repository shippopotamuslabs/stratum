package com.stratum.feature.onboarding

/**
 * Ordered pipeline steps shown on the program-import timeline (Review Import path).
 * Mirrors server + client phases; [ImportProgramPhaseUi.Complete] is not a row (marks all done).
 */
internal val programImportTimelinePhases: List<ImportProgramPhaseUi> =
    listOf(
        ImportProgramPhaseUi.Uploading,
        ImportProgramPhaseUi.Sniffing,
        ImportProgramPhaseUi.Analyzing,
        ImportProgramPhaseUi.Converting,
        ImportProgramPhaseUi.Parsing,
        ImportProgramPhaseUi.MatchingCatalog,
    )

internal fun ImportProgramPhaseUi?.activeImportTimelineIndex(): Int =
    when (this) {
        null -> 0
        ImportProgramPhaseUi.Complete -> programImportTimelinePhases.size
        else -> programImportTimelinePhases.indexOf(this).coerceAtLeast(0)
    }
