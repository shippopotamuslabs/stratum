package com.stratum.domain.program

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.pipeline.PipelinePhase
import com.stratum.domain.program.generation.pipeline.PipelineProgress
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.integration.resolveAdjacentCalendarLiftCollisions
import com.stratum.domain.di.DefaultDispatcher
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import javax.inject.Inject
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Orchestrates on-device program generation: load context → run the deterministic pipeline (with
 * optional lift-swap Callable) → persist → mark onboarding complete.
 *
 * Progress phases are emitted from a background thread as each pipeline step runs — no Firestore
 * polling. Callers should hop to [Dispatchers.Main] when updating UI from [onProgress].
 *
 * Ordering guarantee (D2): generation runs to success **before** persist or completion, so a failed
 * generation never leaves a partial program nor a completed onboarding.
 */
class GenerateProgramUseCase @Inject constructor(
    private val loadContext: ProgramGenerationContextLoader,
    private val pipeline: ProgramGenerationPipeline,
    private val repository: ProgramRepository,
    private val liftRepository: LiftRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val profileRepository: UserTrainingProfileRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository? = null,
    @DefaultDispatcher private val generationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        onProgress: ProgramGenerationProgressCallback = {},
    ): Result<Unit> =
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
                require(program.workouts.isNotEmpty()) {
                    "Generated program has no workouts with known lift references"
                }
                program = bindProgramSchedule(program)
                program = resolveAdjacentCalendarLiftCollisions(
                    program = program,
                    context = context,
                )
                onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.SavingProgram))
                repository.saveProgram(program)
                programChangeEventRepository?.append(
                    ProgramChangeEvent(
                        id = ProgramChangeEventId(UUID.randomUUID().toString()),
                        programId = program.id,
                        eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                        occurredAt = Instant.now(),
                        payload = ProgramChangeEventPayload.ProgramRegenerated,
                    ),
                )
                val profile = profileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
                completeOnboarding(profile)
                onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.Complete))
                Result.success(Unit)
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
}

private fun bindProgramSchedule(program: Program): Program = program

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
