package com.stratum.domain.program

import com.stratum.domain.di.DefaultDispatcher
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.generation.integration.resolveAdjacentCalendarLiftCollisions
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.pipeline.PipelinePhase
import com.stratum.domain.program.generation.pipeline.PipelineProgress
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.schedule.resolveInitialCycleStartEpochDay
import com.stratum.domain.program.schedule.withMaterializedCycleSpace
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
 *
 * Optional [restSlotIndices] / [cycleLength] are raw UX cycle-space hints (PR7) — not a schedule-mode
 * enum. Six-day defaults (8-slot, rests 3+7) and classic weekday complement apply only in
 * [com.stratum.domain.program.schedule.materializeGeneratedCycleSpace].
 */
class GenerateProgramUseCase @Inject constructor(
    private val loadContext: ProgramGenerationContextLoader,
    private val pipeline: ProgramGenerationPipeline,
    private val liftRepository: LiftRepository,
    @DefaultDispatcher private val generationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        restSlotIndices: Set<Int>? = null,
        cycleLength: Int? = null,
        onProgress: ProgramGenerationProgressCallback = {},
    ): Result<Program> =
        withContext(generationDispatcher) {
            try {
                onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.PreparingPayload))
                val context =
                    loadContext
                        .load(
                            restSlotIndices = restSlotIndices,
                            cycleLength = cycleLength,
                        )
                        .withUxCycleSpaceHints(
                            restSlotIndices = restSlotIndices,
                            cycleLength = cycleLength,
                        )
                onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.ReviewingProfile))

                val enabledLifts = liftRepository.observeEnabledLifts().first()
                val liftsById = enabledLifts.associateBy { lift -> lift.id }
                val knownLiftIds = liftsById.keys

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
                    normalized.toDomainProgram(
                        restSecondsForLift = { id -> liftsById[id]?.resolvedDefaultRestSeconds() },
                    )
                        .retainOnlyKnownLifts(knownLiftIds)
                        .withReconciledTrainingWeekdays()
                        // Re-materialize after sanitization so dropped workouts cannot leave
                        // cycleLength/restSlotIndices mismatched to survivors.
                        .withMaterializedCycleSpace(
                            restSlotIndicesHint = context.restSlotIndices,
                            cycleLengthHint = context.cycleLength,
                        )
                // 7-day Mon-start weeks must not anchor slot 0 to "today" (Tue→ first day Tuesday).
                program =
                    program.copy(
                        isActive = false,
                        cycleStartEpochDay = resolveInitialCycleStartEpochDay(program),
                    )
                require(program.workouts.isNotEmpty()) {
                    "Generated program has no workouts with known lift references"
                }
                program =
                    resolveAdjacentCalendarLiftCollisions(
                        program = program,
                        context = context,
                    ).copy(
                        isActive = false,
                        cycleStartEpochDay =
                            program.cycleStartEpochDay
                                ?: resolveInitialCycleStartEpochDay(program),
                    )
                onProgress(ProgramGenerationProgress(ProgramGenerationProgressPhase.Complete))
                Result.success(program)
            } catch (error: kotlinx.coroutines.CancellationException) {
                // Structured cancellation must not become Result.failure.
                throw error
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

/**
 * Merges raw UX cycle-space overrides onto context. Does **not** resolve 6-day defaults —
 * that is sole responsibility of [com.stratum.domain.program.schedule.materializeGeneratedCycleSpace].
 *
 * When both rest pattern and cycle length are present, [ProgramGenerationContext.trainingDaysPerWeek]
 * is set from `cycleLength - rests`.
 */
internal fun ProgramGenerationContext.withUxCycleSpaceHints(
    restSlotIndices: Set<Int>? = null,
    cycleLength: Int? = null,
): ProgramGenerationContext {
    if (restSlotIndices == null && cycleLength == null) return this
    val rests = restSlotIndices ?: this.restSlotIndices
    val length = cycleLength ?: this.cycleLength
    val trainCount =
        if (rests != null && length != null && length > rests.size) {
            length - rests.size
        } else {
            trainingDaysPerWeek
        }
    return copy(
        restSlotIndices = rests,
        cycleLength = length,
        trainingDaysPerWeek = trainCount.coerceAtLeast(1),
    )
}
