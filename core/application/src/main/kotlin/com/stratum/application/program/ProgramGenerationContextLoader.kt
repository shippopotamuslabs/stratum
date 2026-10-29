package com.stratum.application.program

import com.stratum.domain.program.generation.context.ProgramGenerationContext

/**
 * Loads typed [ProgramGenerationContext] from onboarding repositories. Introduced as a seam so
 * [GenerateProgramUseCase] depends on a narrow contract rather than the concrete
 * [LoadProgramGenerationContextUseCase] and its many repository dependencies.
 *
 * Optional [restSlotIndices] / [cycleLength] are UX cycle-space hints applied at load time so
 * [ProgramGenerationContext.trainingDaysPerWeek] resolves without inventing a silent 1-day program.
 */
interface ProgramGenerationContextLoader {
    suspend fun load(
        restSlotIndices: Set<Int>? = null,
        cycleLength: Int? = null,
    ): ProgramGenerationContext
}
