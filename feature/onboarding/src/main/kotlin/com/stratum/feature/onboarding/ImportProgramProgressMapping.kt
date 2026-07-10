package com.stratum.feature.onboarding

import com.stratum.domain.program.importing.ProgramTemplateImportPhase

/** Map the domain import phase to its UI mirror (no domain enum leaks into composables). */
internal fun ProgramTemplateImportPhase.toUi(): ImportProgramPhaseUi = when (this) {
    ProgramTemplateImportPhase.Uploading -> ImportProgramPhaseUi.Uploading
    ProgramTemplateImportPhase.Sniffing -> ImportProgramPhaseUi.Sniffing
    ProgramTemplateImportPhase.Analyzing -> ImportProgramPhaseUi.Analyzing
    ProgramTemplateImportPhase.Converting -> ImportProgramPhaseUi.Converting
    ProgramTemplateImportPhase.Parsing -> ImportProgramPhaseUi.Parsing
    ProgramTemplateImportPhase.MatchingCatalog -> ImportProgramPhaseUi.MatchingCatalog
    ProgramTemplateImportPhase.Complete -> ImportProgramPhaseUi.Complete
    ProgramTemplateImportPhase.Failed -> ImportProgramPhaseUi.MatchingCatalog
}
