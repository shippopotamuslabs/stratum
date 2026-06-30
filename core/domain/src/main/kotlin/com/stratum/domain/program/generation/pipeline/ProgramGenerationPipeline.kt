@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.pipeline

import com.stratum.domain.program.generation.BuildProgramWorkoutsUseCase
import com.stratum.domain.program.generation.IntegrateAndValidateOptions
import com.stratum.domain.program.generation.IntegrateAndValidateProgramUseCase
import com.stratum.domain.program.generation.PlanWeeklyVolumeUseCase
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.prescription.SlotSetAllocationPort
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import javax.inject.Inject

enum class PipelinePhase {
    SplitPlanning,
    BuildingWorkouts,
    Integrating,
}

typealias PipelineProgressCallback = suspend (PipelinePhase) -> Unit

data class PipelineOptions(
    val skipLiftSwap: Boolean = false,
    val skipFinalValidation: Boolean = false,
    val progress: PipelineProgressCallback = {},
)

class ProgramGenerationPipeline @Inject constructor(
    private val planWeeklyVolume: PlanWeeklyVolumeUseCase,
    private val buildProgramWorkouts: BuildProgramWorkoutsUseCase,
    private val integrateAndValidate: IntegrateAndValidateProgramUseCase,
    private val slotSetAllocationPort: SlotSetAllocationPort,
) {
    suspend fun generate(
        context: ProgramGenerationContext,
        normalizationRequest: ProgramNormalizationRequest,
        options: PipelineOptions = PipelineOptions(),
    ): NormalizedProgram {
        options.progress(PipelinePhase.SplitPlanning)
        val weeklyTargets = planWeeklyVolume(context)

        options.progress(PipelinePhase.BuildingWorkouts)
        val buildResult = buildProgramWorkouts(context, weeklyTargets, slotSetAllocationPort)

        options.progress(PipelinePhase.Integrating)
        val draft = ProgramDraft(
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
            options = IntegrateAndValidateOptions(
                skipLiftSwap = options.skipLiftSwap,
                skipFinalValidation = options.skipFinalValidation,
            ),
        )
    }
}
