package com.stratum.core.ui.progress

import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.ProgramGenerationProgressPhase

fun ProgramGenerationProgress.toUi(): ProgramGenerationProgressUi =
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
        ProgramGenerationProgressPhase.PlanningVolume ->
            ProgramGenerationProgressPhaseUi.PlanningVolume
        ProgramGenerationProgressPhase.SynthesizingProgram ->
            ProgramGenerationProgressPhaseUi.SynthesizingProgram
        ProgramGenerationProgressPhase.EvaluatingSplits ->
            ProgramGenerationProgressPhaseUi.EvaluatingSplits
        ProgramGenerationProgressPhase.SelectingExercises ->
            ProgramGenerationProgressPhaseUi.SelectingExercises
        ProgramGenerationProgressPhase.PolishingProgram ->
            ProgramGenerationProgressPhaseUi.PolishingProgram
        ProgramGenerationProgressPhase.FinalizingProgram ->
            ProgramGenerationProgressPhaseUi.FinalizingProgram
        ProgramGenerationProgressPhase.SavingProgram ->
            ProgramGenerationProgressPhaseUi.SavingProgram
        ProgramGenerationProgressPhase.Complete -> ProgramGenerationProgressPhaseUi.Complete
        ProgramGenerationProgressPhase.Failed -> ProgramGenerationProgressPhaseUi.Failed
    }
