@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation

import com.stratum.domain.program.generation.build.BuildProgramResult
import com.stratum.domain.program.generation.build.buildProgramWithSelections
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import javax.inject.Inject

class BuildProgramWorkoutsUseCase @Inject constructor() {
    operator fun invoke(
        context: ProgramGenerationContext,
        weeklyTargets: WeeklyVolumeTargets? = null,
    ): BuildProgramResult = buildProgramWithSelections(context, weeklyTargets)
}
