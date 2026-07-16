package com.stratum.domain.program.importing

data class ParsedProgramTemplate(
    val exportedId: String?,
    val template: NormalizedProgramTemplate,
)
