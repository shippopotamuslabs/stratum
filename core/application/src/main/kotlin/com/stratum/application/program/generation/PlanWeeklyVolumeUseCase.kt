@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.application.program.generation

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import javax.inject.Inject

class PlanWeeklyVolumeUseCase @Inject constructor() {
    operator fun invoke(context: ProgramGenerationContext): WeeklyVolumeTargets =
        planWeeklyVolume(context)
}
