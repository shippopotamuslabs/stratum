package com.stratum.core.ui.progress

/**
 * Ordered pipeline steps shown on the program-generation timeline (onboarding Review and Create Program).
 * [EvaluatingSplits] is omitted from the list — it maps onto the synthesizing step when active.
 */
val programGenerationTimelinePhases: List<ProgramGenerationProgressPhaseUi> =
    listOf(
        ProgramGenerationProgressPhaseUi.PreparingPayload,
        ProgramGenerationProgressPhaseUi.ReviewingProfile,
        ProgramGenerationProgressPhaseUi.PlanningVolume,
        ProgramGenerationProgressPhaseUi.SynthesizingProgram,
        ProgramGenerationProgressPhaseUi.SelectingExercises,
        ProgramGenerationProgressPhaseUi.PolishingProgram,
        ProgramGenerationProgressPhaseUi.FinalizingProgram,
        ProgramGenerationProgressPhaseUi.SavingProgram,
    )

fun ProgramGenerationProgressPhaseUi?.activeTimelineIndex(): Int =
    when (this) {
        null -> 0
        ProgramGenerationProgressPhaseUi.Complete -> programGenerationTimelinePhases.size
        ProgramGenerationProgressPhaseUi.Failed -> 0
        // EvaluatingSplits is not a listed step; indexOf yields -1 → coerced to 0.
        else -> programGenerationTimelinePhases.indexOf(this).coerceAtLeast(0)
    }
