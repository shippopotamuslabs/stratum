@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.pipeline

import com.stratum.domain.program.ProgramGenerationTelemetryLogger
import com.stratum.domain.program.generation.BuildProgramWorkoutsUseCase
import com.stratum.domain.program.generation.IntegrateAndValidateOptions
import com.stratum.domain.program.generation.IntegrateAndValidateProgramUseCase
import com.stratum.domain.program.generation.PlanWeeklyVolumeUseCase
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import javax.inject.Inject

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
    val skipLiftSwap: Boolean = false,
    val skipProgramNaming: Boolean = false,
    val skipFinalValidation: Boolean = false,
    val progress: PipelineProgressCallback = {},
)

class ProgramGenerationPipeline @Inject constructor(
    private val planWeeklyVolume: PlanWeeklyVolumeUseCase,
    private val buildProgramWorkouts: BuildProgramWorkoutsUseCase,
    private val integrateAndValidate: IntegrateAndValidateProgramUseCase,
    private val programSynthesisPort: ProgramSynthesisPort,
    private val telemetryLogger: ProgramGenerationTelemetryLogger,
) {
    suspend fun generate(
        context: ProgramGenerationContext,
        normalizationRequest: ProgramNormalizationRequest,
        options: PipelineOptions = PipelineOptions(),
    ): NormalizedProgram {
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

        val draft =
            ProgramDraft(
                name = buildResult.program.name,
                cycleLength = buildResult.program.cycleLength,
                workouts = buildResult.program.workouts,
            )
        return integrateAndValidate(
            built = draft,
            weeklyTargets = weeklyTargets,
            context = context,
            selectionsBySession = buildResult.selectionsBySession,
            normalizationRequest = normalizationRequest,
            options =
                IntegrateAndValidateOptions(
                    skipLiftSwap = options.skipLiftSwap,
                    skipProgramNaming = options.skipProgramNaming,
                    skipFinalValidation = options.skipFinalValidation,
                ),
            onPolishingProgram = {
                options.progress(PipelineProgress(PipelinePhase.PolishingProgram))
            },
            onFinalizingProgram = {
                options.progress(PipelineProgress(PipelinePhase.FinalizingProgram))
            },
        )
    }
}
