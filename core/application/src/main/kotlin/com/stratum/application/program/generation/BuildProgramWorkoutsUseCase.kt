@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.application.program.generation

import com.stratum.domain.program.generation.build.BuildProgramResult
import com.stratum.domain.program.generation.build.buildProgramWithSelections
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import javax.inject.Inject

class BuildProgramWorkoutsUseCase @Inject constructor() {
    suspend operator fun invoke(
        context: ProgramGenerationContext,
        weeklyTargets: WeeklyVolumeTargets? = null,
        programSynthesisPort: ProgramSynthesisPort,
        onSynthesizingProgram: suspend () -> Unit = {},
        onSelectingExercises: suspend () -> Unit = {},
    ): BuildProgramResult =
        buildProgramWithSelections(
            context = context,
            weeklyTargets = weeklyTargets,
            programSynthesisPort = programSynthesisPort,
            onSynthesizingProgram = onSynthesizingProgram,
            onSelectingExercises = onSelectingExercises,
        )
}
