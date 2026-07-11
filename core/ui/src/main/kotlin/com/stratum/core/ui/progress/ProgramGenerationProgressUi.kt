package com.stratum.core.ui.progress

/**
 * Presentation models for program generation progress. Mirrors domain
 * [com.stratum.domain.program.ProgramGenerationProgressPhase] without leaking domain types into composables.
 */
enum class ProgramGenerationProgressPhaseUi {
    PreparingPayload,
    ReviewingProfile,
    PlanningVolume,
    SynthesizingProgram,
    EvaluatingSplits,
    SelectingExercises,
    PolishingProgram,
    FinalizingProgram,
    SavingProgram,
    Complete,
    Failed,
}

data class ProgramGenerationProgressUi(
    val phase: ProgramGenerationProgressPhaseUi,
    val detail: String? = null,
)
