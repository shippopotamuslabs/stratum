package com.stratum.domain.program

import com.stratum.domain.program.generation.context.ProgramGenerationContext

/**
 * Loads typed [ProgramGenerationContext] from onboarding repositories. Introduced as a seam so
 * [GenerateProgramUseCase] depends on a narrow contract rather than the concrete
 * [LoadProgramGenerationContextUseCase] and its many repository dependencies.
 */
fun interface ProgramGenerationContextLoader {
    suspend fun load(): ProgramGenerationContext
}
