package com.stratum.feature.onboarding

/**
 * Phases emitted during on-device program generation. The timeline shows only live pipeline steps.
 */
internal val programGenerationTimelinePhases: List<ProgramGenerationProgressPhaseUi> =
    listOf(
        ProgramGenerationProgressPhaseUi.PreparingPayload,
        ProgramGenerationProgressPhaseUi.ReviewingProfile,
        ProgramGenerationProgressPhaseUi.SplitPlanning,
        ProgramGenerationProgressPhaseUi.BuildingWorkouts,
        ProgramGenerationProgressPhaseUi.Integrating,
        ProgramGenerationProgressPhaseUi.SavingProgram,
    )

internal enum class ProgramGenerationTimelineStepStatus {
    Completed,
    Active,
    Pending,
}

internal fun ProgramGenerationProgressPhaseUi?.activeTimelineIndex(): Int =
    when (this) {
        null -> 0
        ProgramGenerationProgressPhaseUi.Complete -> programGenerationTimelinePhases.size
        ProgramGenerationProgressPhaseUi.Failed -> 0
        else -> programGenerationTimelinePhases.indexOf(this).coerceAtLeast(0)
    }

internal fun timelineStepStatus(
    stepIndex: Int,
    activeIndex: Int,
): ProgramGenerationTimelineStepStatus =
    when {
        stepIndex < activeIndex -> ProgramGenerationTimelineStepStatus.Completed
        stepIndex == activeIndex -> ProgramGenerationTimelineStepStatus.Active
        else -> ProgramGenerationTimelineStepStatus.Pending
    }
