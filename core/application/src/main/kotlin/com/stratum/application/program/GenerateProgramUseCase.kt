package com.stratum.application.program

import com.stratum.domain.di.DefaultDispatcher
import com.stratum.application.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.generation.integration.resolveAdjacentCalendarLiftCollisions
import com.stratum.application.program.generation.pipeline.PipelineOptions
import com.stratum.application.program.generation.pipeline.PipelinePhase
import com.stratum.application.program.generation.pipeline.PipelineProgress
import com.stratum.application.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.application.program.generation.pipeline.ProgramGenerationPipelineResult
import com.stratum.domain.program.generation.GenerateProgramFailureKind
import com.stratum.domain.program.generation.GenerateProgramResult
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException
import com.stratum.domain.telemetry.TelemetryRouter
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
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.ProgramGenerationProgressCallback
import com.stratum.domain.program.ProgramGenerationProgressPhase
import com.stratum.application.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.program.retainOnlyKnownLifts
import com.stratum.domain.program.schedule.materializeGeneratedCycleSpace
import com.stratum.domain.program.withReconciledTrainingWeekdays

/**
 * Orchestrates on-device program generation: load context → run the deterministic pipeline (with
 * optional lift-swap Callable) → return a [Program] draft.
 *
 * Does **not** persist or complete onboarding. Callers decide persistence:
 * - Onboarding: [ActivateProgramUseCase] + [com.stratum.application.onboarding.CompleteOnboardingUseCase]
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
    private val telemetryRouter: TelemetryRouter,
    @DefaultDispatcher private val generationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        restSlotIndices: Set<Int>? = null,
        cycleLength: Int? = null,
        onProgress: ProgramGenerationProgressCallback = {},
    ): GenerateProgramResult =
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
                    when (
                        val pipelineResult =
                            pipeline.generate(
                                context = context,
                                normalizationRequest = context.toProgramNormalizationRequest(),
                                options =
                                    PipelineOptions(
                                        progress = { progress -> onProgress(progress.toDomainProgress()) },
                                    ),
                            )
                    ) {
                        is ProgramGenerationPipelineResult.Success -> pipelineResult.program
                        is ProgramGenerationPipelineResult.ValidationRejected ->
                            // STR-243: the rejection travels as an outcome branch
                            // carrying the report end-to-end (STR-241 interim mapped
                            // it to a constructed exception value).
                            return@withContext GenerateProgramResult.ValidationRejected(
                                pipelineResult.report,
                            )
                        is ProgramGenerationPipelineResult.SynthesisRefused ->
                            // STR-243: the refusal travels as an outcome branch;
                            // STR-242's classifyGenerateOutcome keys on this branch
                            // at the terminal boundary.
                            return@withContext GenerateProgramResult.SynthesisRefused(
                                pipelineResult.refusal,
                            )
                    }
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
                GenerateProgramResult.Success(program)
            } catch (error: kotlinx.coroutines.CancellationException) {
                // Structured cancellation must not become a failure branch.
                throw error
            } catch (error: ProgramSynthesisException) {
                // Adapter-translated provider condition (quota, timeout, sign-in,
                // monthly limit, …): an expected branch carrying the adapter's
                // user-safe message — never error-logged (STR-242 classifies it
                // expected at the terminal boundary).
                GenerateProgramResult.SynthesisFailed(error.message ?: FALLBACK_MESSAGE)
            } catch (error: Throwable) {
                // Unbounded operational failure: recorded exactly once here at
                // the boundary via the telemetry router (Crashlytics non-fatal
                // + exceptional_occurred counter with the real throwable),
                // restoring the pre-STR-243 exceptional record; the branch
                // carries only the kind (STR-243). Terminal boundaries must
                // not record it again.
                telemetryRouter.recordExceptional(EXCEPTIONAL_BOUNDARY, error)
                GenerateProgramResult.Unexpected(GenerateProgramFailureKind.OperationalFailure)
            }
        }

    private companion object {
        // Boundary name for the exceptional record: the same boundary the
        // Create Program terminal boundary used before STR-243, so the
        // exceptional_occurred counter's boundary/class params are unchanged.
        const val EXCEPTIONAL_BOUNDARY = "CreateProgramGenerate"
        const val FALLBACK_MESSAGE = "Generation failed"
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
