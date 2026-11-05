@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.application.program.generation.pipeline

import com.stratum.domain.program.ProgramGenerationTelemetryLogger
import com.stratum.application.program.generation.BuildProgramWorkoutsUseCase
import com.stratum.application.program.generation.IntegrateAndValidateOptions
import com.stratum.application.program.generation.IntegrateAndValidateProgramResult
import com.stratum.application.program.generation.IntegrateAndValidateProgramUseCase
import com.stratum.application.program.generation.PlanWeeklyVolumeUseCase
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.withResolvedCycleSpace
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.validation.ValidationReport
import javax.inject.Inject
import com.stratum.domain.program.generation.volume.planWeeklyVolume

enum class PipelinePhase {
    PlanningVolume,
    SynthesizingProgram,
    SelectingExercises,
    PolishingProgram,
    FinalizingProgram,
}

data class PipelineProgress(
    val phase: PipelinePhase,
    val detail: String? = null,
)

typealias PipelineProgressCallback = suspend (PipelineProgress) -> Unit

data class PipelineOptions(
    val skipFinalValidation: Boolean = false,
    val progress: PipelineProgressCallback = {},
)

/**
 * Stage outcome of a pipeline run (STR-241): expected stage failures travel as
 * branches of this type instead of thrown exceptions, so [com.stratum.application.program.GenerateProgramUseCase]
 * can map each branch to its failure value explicitly.
 */
sealed interface ProgramGenerationPipelineResult {
    data class Success(val program: NormalizedProgram) : ProgramGenerationPipelineResult
    data class ValidationRejected(val report: ValidationReport) : ProgramGenerationPipelineResult
}

open class ProgramGenerationPipeline @Inject constructor(
    private val planWeeklyVolume: PlanWeeklyVolumeUseCase,
    private val buildProgramWorkouts: BuildProgramWorkoutsUseCase,
    private val integrateAndValidate: IntegrateAndValidateProgramUseCase,
    private val programSynthesisPort: ProgramSynthesisPort,
    private val telemetryLogger: ProgramGenerationTelemetryLogger,
) {
    open suspend fun generate(
        context: ProgramGenerationContext,
        normalizationRequest: ProgramNormalizationRequest,
        options: PipelineOptions = PipelineOptions(),
    ): ProgramGenerationPipelineResult {
        // Shared cycle-space ring for volume planning, synthesis, validation, and post-passes.
        val context = context.withResolvedCycleSpace()
        options.progress(PipelineProgress(PipelinePhase.PlanningVolume))
        val weeklyTargets = planWeeklyVolume(context)

        val buildResult =
            buildProgramWorkouts(
                context = context,
                weeklyTargets = weeklyTargets,
                programSynthesisPort = programSynthesisPort,
                onSynthesizingProgram = {
                    options.progress(PipelineProgress(PipelinePhase.SynthesizingProgram))
                },
                onSelectingExercises = {
                    options.progress(PipelineProgress(PipelinePhase.SelectingExercises))
                },
            )
        telemetryLogger.logSynthesisComplete(context, buildResult)
        // STR-254: drain slot-build diagnostics exactly once per generation through the
        // pipeline's existing logging wiring. Diagnostics never reach the UI.
        telemetryLogger.logGenerationDiagnostics(buildResult.diagnostics)

        val draft =
            ProgramDraft(
                name = buildResult.program.name,
                cycleLength = buildResult.program.cycleLength,
                restSlotIndices = buildResult.program.restSlotIndices,
                workouts = buildResult.program.workouts,
            )
        return when (
            val integrated = integrateAndValidate(
                built = draft,
                weeklyTargets = weeklyTargets,
                context = context,
                normalizationRequest = normalizationRequest,
                options =
                    IntegrateAndValidateOptions(
                        skipFinalValidation = options.skipFinalValidation,
                    ),
                onPolishingProgram = {
                    options.progress(PipelineProgress(PipelinePhase.PolishingProgram))
                },
                onFinalizingProgram = {
                    options.progress(PipelineProgress(PipelinePhase.FinalizingProgram))
                },
            )
        ) {
            is IntegrateAndValidateProgramResult.Success ->
                ProgramGenerationPipelineResult.Success(integrated.program)
            is IntegrateAndValidateProgramResult.ValidationRejected ->
                ProgramGenerationPipelineResult.ValidationRejected(integrated.report)
        }
    }
}
