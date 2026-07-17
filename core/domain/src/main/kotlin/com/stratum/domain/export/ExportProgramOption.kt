package com.stratum.domain.export

import com.stratum.domain.program.identity.ProgramId

data class ExportProgramOption(
    val id: ProgramId,
    val name: String,
    val isActive: Boolean,
)
