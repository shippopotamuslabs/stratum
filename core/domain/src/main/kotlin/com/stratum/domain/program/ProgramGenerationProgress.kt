package com.stratum.domain.program


/**
 * Client-visible phases during on-device program generation. Emitted locally by
 * [GenerateProgramUseCase] and the domain pipeline — no server polling.
 */
enum class ProgramGenerationProgressPhase {
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

typealias ProgramGenerationProgressCallback = suspend (ProgramGenerationProgress) -> Unit

data class ProgramGenerationProgress(
    val phase: ProgramGenerationProgressPhase,
    val detail: String? = null,
)
