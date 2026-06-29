package com.stratum.domain.program

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.pipeline.PipelinePhase
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Orchestrates on-device program generation: load context → run the deterministic pipeline (with
 * optional lift-swap Callable) → persist → mark onboarding complete.
 *
 * Progress phases are emitted synchronously as each pipeline step runs — no Firestore polling.
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
) {
    suspend operator fun invoke(
        onProgress: ProgramGenerationProgressCallback = {},
    ): Result<Unit> =
        try {
            onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.PreparingPayload))
            val context = loadContext.load()
            onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.ReviewingProfile))

            val knownLiftIds =
                liftRepository.observeLifts().first()
                    .filter { lift -> lift.enabled }
                    .map { lift -> lift.id }
                    .toSet()

            val normalized =
                pipeline.generate(
                    context = context,
                    normalizationRequest = context.toProgramNormalizationRequest(),
                    options =
                        PipelineOptions(
                            progress = { phase -> onProgress(phase.toDomainProgress()) },
                        ),
                )
            var program =
                normalized.toDomainProgram()
                    .retainOnlyKnownLifts(knownLiftIds)
            require(program.workouts.isNotEmpty()) {
                "Generated program has no workouts with known lift references"
            }
            val trainingWeekdays =
                context.weekdays.mapNotNull { weekday ->
                    runCatching { Weekday.valueOf(weekday) }.getOrNull()
                }
            program = assignProgramSchedule(program, trainingWeekdays)
            onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.SavingProgram))
            repository.saveProgram(program)
            val profile = profileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
            completeOnboarding(profile)
            onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.Complete))
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(error)
        }
}

private fun PipelinePhase.toDomainProgress(): ProgramGenerationProgress =
    ProgramGenerationProgress(
        phase =
            when (this) {
                PipelinePhase.SplitPlanning -> ProgramGenerationProgressPhase.SplitPlanning
                PipelinePhase.BuildingWorkouts -> ProgramGenerationProgressPhase.BuildingWorkouts
                PipelinePhase.Integrating -> ProgramGenerationProgressPhase.Integrating
            },
    )
