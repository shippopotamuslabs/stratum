package com.stratum.feature.program

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
import com.stratum.domain.logging.LogAnalyticsEventUseCase
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.telemetry.ClientStage
import com.stratum.domain.telemetry.SynthesisTelemetry
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_CLIENT_STAGE
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_RENDERED
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_DURATION_MS
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_STAGE
import com.stratum.domain.logging.PARAM_ACTION
import com.stratum.domain.logging.PARAM_SCREEN
import com.stratum.domain.logging.USER_VISIBLE_ERROR_EVENT
import com.stratum.domain.onboarding.GetUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.domain.program.InferLastTrainedBucketUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class CreateProgramGenerateControllerTest {

    @Test
    fun `generate failure records analytics and surfaces error state`() = runTest {
        val analytics = FakeAnalyticsPort()
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
                        loadContext = FixedProgramGenerationContextLoader(error = { error("boom") }),
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
                logError = LogErrorUseCase(FakeLoggingPort()),
                logAnalytics = LogAnalyticsEventUseCase(analytics),
                synthesisTelemetry = SynthesisTelemetry(analytics::logEvent),
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
        val event = analytics.events.single()
        assertEquals(USER_VISIBLE_ERROR_EVENT, event.name)
        assertEquals("create_program", event.params[PARAM_SCREEN])
        assertEquals("generate_program", event.params[PARAM_ACTION])
    }

    @Test
    fun `generate success emits client stage timings and rendered event`() = runTest {
        val analytics = FakeAnalyticsPort()
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
                logError = LogErrorUseCase(FakeLoggingPort()),
                logAnalytics = LogAnalyticsEventUseCase(analytics),
                synthesisTelemetry = SynthesisTelemetry(analytics::logEvent),
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
        assertEquals(1, byName[EVENT_SYNTHESIS_RENDERED].orEmpty().size)
    }
}
