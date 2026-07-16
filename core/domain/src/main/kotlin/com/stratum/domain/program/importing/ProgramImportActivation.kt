package com.stratum.domain.program.importing

enum class ProgramImportActivation {
    /** Onboarding wizard path: save with isActive=true (existing behavior). */
    ActivateAsActive,

    /** Settings: ProgramRepository.saveToLibrary only; never force-activate. */
    LibraryOnly,
}
