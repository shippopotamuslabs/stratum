package com.stratum.feature.onboarding

import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.ProgramGenerationProgressPhase

internal fun ProgramGenerationProgress.toUi(): ProgramGenerationProgressUi =
    ProgramGenerationProgressUi(
        phase = phase.toUi(),
        detail = detail,
    )

private fun ProgramGenerationProgressPhase.toUi(): ProgramGenerationProgressPhaseUi =
    when (this) {
        ProgramGenerationProgressPhase.PreparingPayload ->
            ProgramGenerationProgressPhaseUi.PreparingPayload
        ProgramGenerationProgressPhase.ReviewingProfile ->
            ProgramGenerationProgressPhaseUi.ReviewingProfile
        ProgramGenerationProgressPhase.SplitPlanning ->
            ProgramGenerationProgressPhaseUi.SplitPlanning
        ProgramGenerationProgressPhase.BuildingWorkouts ->
            ProgramGenerationProgressPhaseUi.BuildingWorkouts
        ProgramGenerationProgressPhase.Integrating ->
            ProgramGenerationProgressPhaseUi.Integrating
        ProgramGenerationProgressPhase.SavingProgram ->
            ProgramGenerationProgressPhaseUi.SavingProgram
        ProgramGenerationProgressPhase.Complete -> ProgramGenerationProgressPhaseUi.Complete
        ProgramGenerationProgressPhase.Failed -> ProgramGenerationProgressPhaseUi.Failed
    }
