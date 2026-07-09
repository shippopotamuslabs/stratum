package com.stratum.domain.program

import com.stratum.domain.di.DefaultDispatcher
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.generation.integration.resolveAdjacentCalendarLiftCollisions
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.pipeline.PipelinePhase
import com.stratum.domain.program.generation.pipeline.PipelineProgress
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Orchestrates on-device program generation: load context → run the deterministic pipeline (with
 * optional lift-swap Callable) → return a [Program] draft.
 *
 * Does **not** persist or complete onboarding. Callers decide persistence:
 * - Onboarding: [ActivateProgramUseCase] + [com.stratum.domain.onboarding.CompleteOnboardingUseCase]
 * - Create Program: hold draft until Save to Library / Activate
 *
 * Progress phases are emitted from a background thread as each pipeline step runs. Callers should
 * hop to [Dispatchers.Main] when updating UI from [onProgress].
 */
class GenerateProgramUseCase @Inject constructor(
    private val loadContext: ProgramGenerationContextLoader,
    private val pipeline: ProgramGenerationPipeline,
    private val liftRepository: LiftRepository,
    @DefaultDispatcher private val generationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        onProgress: ProgramGenerationProgressCallback = {},
    ): Result<Program> =
        withContext(generationDispatcher) {
            try {
                onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.PreparingPayload))
                val context = loadContext.load()
                onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.ReviewingProfile))

                val knownLiftIds =
                    liftRepository.observeEnabledLifts().first()
                        .map { lift -> lift.id }
                        .toSet()

                val normalized =
                    pipeline.generate(
                        context = context,
                        normalizationRequest = context.toProgramNormalizationRequest(),
                        options =
                            PipelineOptions(
                                progress = { progress -> onProgress(progress.toDomainProgress()) },
                            ),
                    )
                var program =
                    normalized.toDomainProgram()
                        .retainOnlyKnownLifts(knownLiftIds)
                        .withReconciledTrainingWeekdays()
                        .copy(isActive = false)
                require(program.workouts.isNotEmpty()) {
                    "Generated program has no workouts with known lift references"
                }
                require(program.effectiveTrainingWeekdays().isNotEmpty()) {
                    "Generated program has no bound training weekdays"
                }
                program = resolveAdjacentCalendarLiftCollisions(
                    program = program,
                    context = context,
                ).copy(isActive = false)
                onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.Complete))
                Result.success(program)
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
}

private fun PipelineProgress.toDomainProgress(): ProgramGenerationProgress =
    ProgramGenerationProgress(
        phase =
            when (phase) {
                PipelinePhase.PlanningVolume -> ProgramGenerationProgressPhase.PlanningVolume
                PipelinePhase.SynthesizingProgram -> ProgramGenerationProgressPhase.SynthesizingProgram
                PipelinePhase.SelectingExercises -> ProgramGenerationProgressPhase.SelectingExercises
                PipelinePhase.PolishingProgram -> ProgramGenerationProgressPhase.PolishingProgram
                PipelinePhase.FinalizingProgram -> ProgramGenerationProgressPhase.FinalizingProgram
            },
        detail = detail,
    )
