package com.stratum.core.testing.program

import com.stratum.application.program.ProgramGenerationContextLoader
import com.stratum.domain.program.generation.context.ProgramGenerationContext

/** Test double that always returns a fixed [ProgramGenerationContext]. */
class FixedProgramGenerationContextLoader(
    private val context: ProgramGenerationContext? = null,
    private val error: (() -> Nothing)? = null,
) : ProgramGenerationContextLoader {
    override suspend fun load(
        restSlotIndices: Set<Int>?,
        cycleLength: Int?,
    ): ProgramGenerationContext {
        error?.invoke()
        return checkNotNull(context) { "FixedProgramGenerationContextLoader has no context" }
    }
}
