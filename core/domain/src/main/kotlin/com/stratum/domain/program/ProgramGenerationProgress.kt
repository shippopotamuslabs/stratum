package com.stratum.domain.program

/**
 * Client-visible phases during program generation. Server `progressPhase` strings map in
 * `:core:inference`; local-only phases are emitted by [GenerateProgramUseCase] before/after the
 * callable.
 */
enum class ProgramGenerationProgressPhase {
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

typealias ProgramGenerationProgressCallback = suspend (ProgramGenerationProgress) -> Unit

data class ProgramGenerationProgress(
    val phase: ProgramGenerationProgressPhase,
    val detail: String? = null,
)
