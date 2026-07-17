package com.stratum.domain.program.importing

data class ProgramTemplateImportOptions(
    val activation: ProgramImportActivation,
    val completeOnboarding: Boolean,
    val appendProgramRegeneratedEvent: Boolean,
)
