package com.stratum.domain.program.importing

import com.stratum.domain.program.identity.ProgramId

data class ProgramTemplateImportResult(
    val importedProgramIds: List<ProgramId>,
    val skippedProgramCount: Int,
)
