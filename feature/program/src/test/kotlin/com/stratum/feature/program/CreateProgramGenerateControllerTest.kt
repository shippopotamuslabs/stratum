package com.stratum.feature.program

import com.stratum.core.infrastructure.telemetry.ProgramGenerationTelemetryLogger
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.ui.trainingsetup.SESSION_BAND_KEY_MEDIUM
import com.stratum.core.ui.trainingsetup.ScheduleDraftUi
import com.stratum.core.ui.trainingsetup.isReadyForGeneration
import com.stratum.core.ui.trainingsetup.withSyncedCycleSpace
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FixedProgramGenerationContextLoader
import com.stratum.core.testing.program.SCENARIO_LIFT_CATALOG
import com.stratum.core.testing.program.scenarioContext
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.core.testing.telemetry.FakeAnalyticsPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.core.testing.telemetry.FakeCrashTelemetryPort
import com.stratum.core.testing.telemetry.FakeOutcomeTelemetrySink
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException
import com.stratum.core.infrastructure.telemetry.ClientStage
import com.stratum.core.infrastructure.telemetry.OutcomeResult
import com.stratum.core.infrastructure.telemetry.OutcomeTelemetry
import com.stratum.core.infrastructure.telemetry.SynthesisTelemetry
import com.stratum.core.infrastructure.telemetry.TelemetryRouter
import com.stratum.core.infrastructure.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_CLIENT_STAGE
import com.stratum.core.infrastructure.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_RENDERED
import com.stratum.core.infrastructure.telemetry.SynthesisTelemetry.Companion.PARAM_DURATION_MS
import com.stratum.core.infrastructure.telemetry.SynthesisTelemetry.Companion.PARAM_JOB_ID
import com.stratum.core.infrastructure.telemetry.SynthesisTelemetry.Companion.PARAM_SESSION_ID
import com.stratum.core.infrastructure.telemetry.SynthesisTelemetry.Companion.PARAM_STAGE
import com.stratum.core.infrastructure.telemetry.PARAM_ACTION
import com.stratum.core.infrastructure.telemetry.PARAM_SCREEN
import com.stratum.core.infrastructure.telemetry.USER_VISIBLE_ERROR_EVENT
import com.stratum.application.onboarding.GetUserTrainingProfileUseCase
import com.stratum.application.onboarding.UpdateSpecializationUseCase
import com.stratum.application.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.application.program.GenerateProgramUseCase
import com.stratum.application.program.InferLastTrainedBucketUseCase
import com.stratum.application.program.generation.BuildProgramWorkoutsUseCase
import com.stratum.application.program.generation.IntegrateAndValidateProgramUseCase
import com.stratum.application.program.generation.PlanWeeklyVolumeUseCase
import com.stratum.application.program.generation.pipeline.PipelineOptions
import com.stratum.application.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.application.program.generation.pipeline.ProgramGenerationPipelineResult
import com.stratum.core.testing.program.FakeProgramSynthesisPort
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.GenerationSynthesisSummary
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.schema.NormalizedLift
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.NormalizedSet
import com.stratum.domain.program.generation.schema.NormalizedWorkout
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import com.stratum.domain.program.generation.slots.SlotGenerationDiagnostic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class CreateProgramGenerateControllerTest {

    @Test
    fun `generate failure records analytics and surfaces error state`() = runTest {
        // STR-237 Special 1: a distinctive pre-created throwable — the record
        // must carry THIS instance (class fidelity), not a constructed value.
        val boom = IllegalArgumentException("boom")
        val analytics = FakeAnalyticsPort()
        val outcomes = FakeOutcomeTelemetrySink()
        val crash = FakeCrashTelemetryPort()
        val profileRepo = FakeUserTrainingProfileRepository()
        val state =
            MutableStateFlow(
                CreateProgramUiState(
                    scheduleDraft =
                        ScheduleDraftUi(
                            sessionLengthSelection = SESSION_BAND_KEY_MEDIUM,
                        ),
                ),
            )
        val controller =
            CreateProgramGenerateController(
                state = state,
                scope = backgroundScope,
                generateProgram =
                    GenerateProgramUseCase(
                        loadContext =
                            FixedProgramGenerationContextLoader(error = { throw boom }),
                        pipeline = testProgramGenerationPipeline(),
                        liftRepository = com.stratum.core.testing.onboarding.FakeLiftRepository(),
                        generationDispatcher = UnconfinedTestDispatcher(testScheduler),
                    ),
                updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(profileRepo),
                updateSpecialization =
                    UpdateSpecializationUseCase(FakeSpecializationPreferenceRepository()),
                getUserTrainingProfile = GetUserTrainingProfileUseCase(profileRepo),
                inferLastTrained = InferLastTrainedBucketUseCase(FakeTrainingLogRepository()),
                onProgramGenerated = {},
                logAnalytics = AnalyticsRecorder(analytics),
                synthesisTelemetry = SynthesisTelemetry(analytics::logEvent),
                telemetryRouter = TelemetryRouter(outcomes, crash, analytics),
                generationLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
            )

        assertTrue(
            state.value.scheduleDraft
                .withSyncedCycleSpace()
                .isReadyForGeneration(),
            "test draft must satisfy the generation readiness gate",
        )
        controller.generate()
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertEquals(CreateProgramGenerateState.Error, state.value.generateState)
        // STR-243: an unexpected failure surfaces the generic fallback copy,
        // never the raw exception message.
        assertEquals("Generation failed", state.value.generateErrorMessage)
        val byName = analytics.events.groupBy { it.name }
        val event = byName[USER_VISIBLE_ERROR_EVENT]?.single()
        assertEquals("create_program", event?.params?.get(PARAM_SCREEN))
        assertEquals("generate_program", event?.params?.get(PARAM_ACTION))
        // Unexpected failure: never in the outcome stream; STR-237 Special 1
        // carries the REAL throwable on the branch and the controller records
        // it exactly once via TelemetryRouter.recordExceptional: one
        // non-fatal plus one exceptional_occurred counter increment with the
        // base boundary/class params. Class fidelity: the recorded exception
        // IS the fixture's throwable (assertSame), and the class param names
        // its real class.
        assertTrue(outcomes.outcomes.isEmpty())
        assertEquals(1, crash.recordedExceptions.size)
        assertSame(boom, crash.recordedExceptions.single())
        val exceptional = byName[TelemetryRouter.EVENT_EXCEPTIONAL_OCCURRED]?.single()
        assertEquals("CreateProgramGenerate", exceptional?.params?.get(TelemetryRouter.PARAM_BOUNDARY))
        assertEquals("IllegalArgumentException", exceptional?.params?.get(TelemetryRouter.PARAM_CLASS))
    }

    @Test
    fun `post-pipeline unexpected failure records carried synthesis exactly once`() = runTest {
        // STR-237 review round 1: the pipeline succeeds and carries a
        // synthesis summary with one slot diagnostic, but every built lift
        // is unknown to the (empty) catalog, so post-pipeline sanitization
        // empties the program and generation ends Unexpected. The boundary
        // must record the synthesis exactly once (one warning for the one
        // diagnostic) in addition to the exceptional record.
        val analytics = FakeAnalyticsPort()
        val outcomes = FakeOutcomeTelemetrySink()
        val crash = FakeCrashTelemetryPort()
        val generationLogging = FakeLoggingPort()
        val profileRepo = FakeUserTrainingProfileRepository()
        val state =
            MutableStateFlow(
                CreateProgramUiState(
                    scheduleDraft =
                        ScheduleDraftUi(
                            sessionLengthSelection = SESSION_BAND_KEY_MEDIUM,
                        ),
                ),
            )
        val summary =
            GenerationSynthesisSummary(
                trainingDaysPerWeek = 3,
                cycleLength = 7,
                objective = SynthesisObjectiveBreakdown.ZERO,
                diagnostics =
                    listOf(
                        SlotGenerationDiagnostic.CrossDomainTouchSkipped(
                            patternName = "BogusPattern",
                            muscle = MuscleGroup.Chest,
                        ),
                    ),
            )
        val controller =
            CreateProgramGenerateController(
                state = state,
                scope = backgroundScope,
                generateProgram =
                    GenerateProgramUseCase(
                        loadContext =
                            FixedProgramGenerationContextLoader(
                                context = scenarioContext(days = 3, specialization = emptyMap()),
                            ),
                        pipeline = UnknownLiftSynthesisPipeline(summary),
                        liftRepository = com.stratum.core.testing.onboarding.FakeLiftRepository(),
                        generationDispatcher = UnconfinedTestDispatcher(testScheduler),
                    ),
                updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(profileRepo),
                updateSpecialization =
                    UpdateSpecializationUseCase(FakeSpecializationPreferenceRepository()),
                getUserTrainingProfile = GetUserTrainingProfileUseCase(profileRepo),
                inferLastTrained = InferLastTrainedBucketUseCase(FakeTrainingLogRepository()),
                onProgramGenerated = {},
                logAnalytics = AnalyticsRecorder(analytics),
                synthesisTelemetry = SynthesisTelemetry(analytics::logEvent),
                telemetryRouter = TelemetryRouter(outcomes, crash, analytics),
                generationLogger = ProgramGenerationTelemetryLogger(generationLogging),
            )

        controller.generate()
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertEquals(CreateProgramGenerateState.Error, state.value.generateState)
        // The exceptional record still fires exactly once for the require failure.
        assertEquals(1, crash.recordedExceptions.size)
        assertTrue(crash.recordedExceptions.single() is IllegalArgumentException)
        // The synthesis record fires exactly once: one warning for the one
        // carried slot diagnostic (a second recordSynthesis would double it).
        val warning = generationLogging.warnings.single()
        assertEquals("StratumProgramGeneration", warning.tag)
        assertTrue(warning.message.contains("Cross-domain touch skipped"))
    }

    @Test
    fun `translated synthesis failure emits expected outcome without error log`() = runTest {
        val analytics = FakeAnalyticsPort()
        val outcomes = FakeOutcomeTelemetrySink()
        val crash = FakeCrashTelemetryPort()
        val profileRepo = FakeUserTrainingProfileRepository()
        val state =
            MutableStateFlow(
                CreateProgramUiState(
                    scheduleDraft =
                        ScheduleDraftUi(
                            sessionLengthSelection = SESSION_BAND_KEY_MEDIUM,
                        ),
                ),
            )
        val controller =
            CreateProgramGenerateController(
                state = state,
                scope = backgroundScope,
                generateProgram =
                    GenerateProgramUseCase(
                        loadContext =
                            FixedProgramGenerationContextLoader(
                                error = { throw ProgramSynthesisException("quota exhausted") },
                            ),
                        pipeline = testProgramGenerationPipeline(),
                        liftRepository = com.stratum.core.testing.onboarding.FakeLiftRepository(),
                        generationDispatcher = UnconfinedTestDispatcher(testScheduler),
                    ),
                updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(profileRepo),
                updateSpecialization =
                    UpdateSpecializationUseCase(FakeSpecializationPreferenceRepository()),
                getUserTrainingProfile = GetUserTrainingProfileUseCase(profileRepo),
                inferLastTrained = InferLastTrainedBucketUseCase(FakeTrainingLogRepository()),
                onProgramGenerated = {},
                logAnalytics = AnalyticsRecorder(analytics),
                synthesisTelemetry = SynthesisTelemetry(analytics::logEvent),
                telemetryRouter = TelemetryRouter(outcomes, crash, analytics),
                generationLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
            )

        controller.generate()
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertEquals(CreateProgramGenerateState.Error, state.value.generateState)
        // The adapter's user-safe message is the surfaced copy, unchanged.
        assertEquals("quota exhausted", state.value.generateErrorMessage)
        val outcome = outcomes.outcomes.single()
        assertEquals(OutcomeTelemetry.USE_CASE_PROGRAM_GENERATION, outcome.useCase)
        assertEquals(
            OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNTHESIS_FAILED),
            outcome.result,
        )
        assertTrue(crash.recordedExceptions.isEmpty(), "expected failure must not touch the crash path")
        assertTrue(
            analytics.events.none { it.name == TelemetryRouter.EVENT_EXCEPTIONAL_OCCURRED },
            "expected failure must not increment the exceptional counter",
        )
    }

    @Test
    fun `generate success emits client stage timings and rendered event`() = runTest {
        val analytics = FakeAnalyticsPort()
        val outcomes = FakeOutcomeTelemetrySink()
        val crash = FakeCrashTelemetryPort()
        val profileRepo = FakeUserTrainingProfileRepository()
        val state =
            MutableStateFlow(
                CreateProgramUiState(
                    scheduleDraft =
                        ScheduleDraftUi(
                            sessionLengthSelection = SESSION_BAND_KEY_MEDIUM,
                        ),
                ),
            )
        var generatedProgram: com.stratum.domain.program.Program? = null
        val controller =
            CreateProgramGenerateController(
                state = state,
                scope = backgroundScope,
                generateProgram =
                    GenerateProgramUseCase(
                        loadContext =
                            FixedProgramGenerationContextLoader(
                                context = scenarioContext(days = 3, specialization = emptyMap()),
                            ),
                        pipeline = testProgramGenerationPipeline(),
                        liftRepository =
                            FakeLiftRepository(
                                initialLifts =
                                    SCENARIO_LIFT_CATALOG.map { id ->
                                        Lift(
                                            id = LiftId(id.id),
                                            name = id.id,
                                            movementPatterns = emptySet(),
                                            primaryMuscles = emptySet(),
                                            secondaryMuscles = emptySet(),
                                            incrementTiers = emptyList(),
                                            presetTags = emptySet(),
                                            enabled = true,
                                        )
                                    },
                            ),
                        generationDispatcher = UnconfinedTestDispatcher(testScheduler),
                    ),
                updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(profileRepo),
                updateSpecialization =
                    UpdateSpecializationUseCase(FakeSpecializationPreferenceRepository()),
                getUserTrainingProfile = GetUserTrainingProfileUseCase(profileRepo),
                inferLastTrained = InferLastTrainedBucketUseCase(FakeTrainingLogRepository()),
                onProgramGenerated = { program -> generatedProgram = program },
                logAnalytics = AnalyticsRecorder(analytics),
                synthesisTelemetry = SynthesisTelemetry(analytics::logEvent),
                telemetryRouter = TelemetryRouter(outcomes, crash, analytics),
                generationLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
            )

        controller.generate()
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertTrue(generatedProgram != null, "expected a generated program on success")
        val byName = analytics.events.groupBy { it.name }
        val stages = byName[EVENT_SYNTHESIS_CLIENT_STAGE].orEmpty()
        assertEquals(
            setOf(
                ClientStage.GENERATE_PROGRAM.wireName,
                ClientStage.LOAD_PROGRAM_INTO_EDITOR.wireName,
            ),
            stages.map { it.params[PARAM_STAGE] }.toSet(),
        )
        stages.forEach { event ->
            val durationMs = event.params[PARAM_DURATION_MS]?.toLongOrNull()
            assertTrue(durationMs != null && durationMs >= 0, "stage duration must be present")
        }
        val rendered = byName[EVENT_SYNTHESIS_RENDERED].orEmpty()
        assertEquals(1, rendered.size)
        val renderedParams = rendered.single().params
        val renderedDurationMs = renderedParams[PARAM_DURATION_MS]?.toLongOrNull()
        assertTrue(
            renderedDurationMs != null && renderedDurationMs >= 0,
            "rendered duration must be present",
        )
        // Rendered is session-keyed: the cloud jobId is invisible through
        // GenerateProgramUseCase, so job_id must be omitted, never overloaded.
        assertTrue(!renderedParams[PARAM_SESSION_ID].isNullOrBlank(), "session_id must be present")
        assertNull(renderedParams[PARAM_JOB_ID], "job_id must not carry a session id")
        // STR-242: success outcome on the countable stream for the rate denominator.
        val outcome = outcomes.outcomes.single()
        assertEquals(OutcomeTelemetry.USE_CASE_PROGRAM_GENERATION, outcome.useCase)
        assertEquals(OutcomeResult.Success, outcome.result)
        val outcomeDuration = outcome.durationMs
        assertTrue(outcomeDuration != null && outcomeDuration >= 0, "outcome duration must be present")
        assertTrue(crash.recordedExceptions.isEmpty())
        assertTrue(
            analytics.events.none { it.name == TelemetryRouter.EVENT_EXCEPTIONAL_OCCURRED },
            "success must not increment the exceptional counter",
        )
    }
}

/**
 * Test double: returns a one-workout program whose only lift is unknown to
 * the test's empty catalog, plus a carried [GenerationSynthesisSummary], so
 * the post-pipeline failure boundary test exercises the real
 * [GenerateProgramUseCase] sanitization path (STR-237 review round 1).
 */
private class UnknownLiftSynthesisPipeline(
    private val synthesis: GenerationSynthesisSummary,
) : ProgramGenerationPipeline(
    planWeeklyVolume = PlanWeeklyVolumeUseCase(),
    buildProgramWorkouts = BuildProgramWorkoutsUseCase(),
    integrateAndValidate = IntegrateAndValidateProgramUseCase(),
    programSynthesisPort = FakeProgramSynthesisPort(),
) {
    override suspend fun generate(
        context: ProgramGenerationContext,
        normalizationRequest: ProgramNormalizationRequest,
        options: PipelineOptions,
    ): ProgramGenerationPipelineResult =
        ProgramGenerationPipelineResult.Success(
            program =
                NormalizedProgram(
                    name = "Unknown Lifts Program",
                    cycleLength = 1,
                    restSlotIndices = emptySet(),
                    workouts =
                        listOf(
                            NormalizedWorkout(
                                name = "Day 1",
                                weekday = "Monday",
                                position = 0,
                                workoutLifts =
                                    listOf(
                                        NormalizedLift(
                                            liftId = "unknown-lift",
                                            position = 0,
                                            progressionScheme = ProgressionScheme.Double,
                                            workoutLiftSets =
                                                listOf(
                                                    NormalizedSet(
                                                        position = 0,
                                                        setType = SetType.Standard,
                                                        repMin = 6,
                                                        repMax = 10,
                                                        rirGoal = 2,
                                                        isTopSet = true,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                ),
            synthesis = synthesis,
        )
}
