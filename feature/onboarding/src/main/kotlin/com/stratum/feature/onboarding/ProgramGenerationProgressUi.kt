package com.stratum.feature.onboarding

/**
 * Presentation models for program generation progress. Mirrors [ProgramGenerationProgressPhase]
 * without leaking domain types into composables.
 */
enum class ProgramGenerationProgressPhaseUi {
    PreparingPayload,
    ReviewingProfile,
    ValidatingProgram,
    DraftingProgram,
    RefiningProgram,
    SplitPlanning,
    BuildingWorkouts,
    Integrating,
    SavingProgram,
    Complete,
    Failed,
}

data class ProgramGenerationProgressUi(
    val phase: ProgramGenerationProgressPhaseUi,
    val detail: String? = null,
)
