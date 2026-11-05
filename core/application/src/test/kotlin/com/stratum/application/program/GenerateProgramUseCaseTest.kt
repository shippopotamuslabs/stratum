package com.stratum.application.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.program.FakeProgramSynthesisPort
import com.stratum.core.testing.program.FixedProgramGenerationContextLoader
import com.stratum.core.testing.program.SCENARIO_LIFT_CATALOG
import com.stratum.core.testing.program.scenarioContext
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.schema.NormalizedLift
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.NormalizedSet
import com.stratum.domain.program.generation.schema.NormalizedWorkout
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import com.stratum.domain.program.generation.GenerateProgramFailureKind
import com.stratum.domain.program.generation.GenerateProgramResult
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisRefusal
import com.stratum.domain.program.generation.validation.ValidationIssue
import com.stratum.domain.program.generation.validation.ValidationReport
import com.stratum.domain.program.generation.validation.ValidationSeverity
import com.stratum.domain.program.generation.validation.ValidationSummary
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.application.program.generation.BuildProgramWorkoutsUseCase
import com.stratum.application.program.generation.IntegrateAndValidateProgramUseCase
import com.stratum.application.program.generation.PlanWeeklyVolumeUseCase
import com.stratum.application.program.generation.pipeline.PipelineOptions
import com.stratum.application.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.application.program.generation.pipeline.ProgramGenerationPipelineResult
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGenerationProgressPhase
import com.stratum.domain.program.ProgramGenerationTelemetryLogger
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.SetType
import com.stratum.domain.program.buildWeeklySchedule
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.retainOnlyKnownLifts
import com.stratum.domain.program.ProgressionScheme

/**
 * [GenerateProgramUseCase] orchestration tests. Generation returns a draft; persistence is the
 * caller's responsibility ([ActivateProgramUseCase] / [SaveProgramToLibraryUseCase]).
 */
class GenerateProgramUseCaseTest {
    @Nested
    @DisplayName("PROG-GEN-100 happy path returns a generated program draft")
    inner class HappyPath {
        @Test
        fun `invoke returns a generated program without persisting`() = runTest {
            val useCase = buildUseCase(this)

            val result = useCase.invoke()

            assertTrue(result is GenerateProgramResult.Success)
            assertTrue(programRepository.saveProgramCalls.isEmpty())
            assertNull(programRepository.current)
            assertFalse(result.requireProgram().isActive)
        }

        @Test
        fun `invoke binds training weekdays so weekly schedule has workout days`() = runTest {
            val useCase = buildUseCase(this)

            val program = useCase.invoke().requireProgram()

            val workoutDays =
                buildWeeklySchedule(program).filterIsInstance<ScheduleDayItem.WorkoutDay>()
            assertTrue(workoutDays.isNotEmpty(), "expected workout days in weekly schedule")
            assertEquals(program.workouts.size, workoutDays.size)
            assertTrue(program.trainingWeekdays.isNotEmpty())
            assertEquals(
                program.workouts.mapNotNull { it.weekday }.toSet(),
                program.trainingWeekdays,
            )
        }

        @Test
        @DisplayName("retainOnlyKnownLifts dropping workouts keeps trainingWeekdays consistent with survivors")
        fun `invoke keeps training weekdays consistent when sanitization drops workouts`() = runTest {
            val upperOnlyLiftIds =
                setOf(
                    "bench",
                    "db-press",
                    "inc-press",
                    "row",
                    "seated-row",
                    "pullup",
                    "lat-raise",
                    "rear-delt-fly",
                    "ohp",
                    "curl",
                    "tricep-ext",
                ).map { LiftId(it) }.toSet()
            val useCase = buildUseCase(this, enabledLiftIds = upperOnlyLiftIds)

            val result = useCase.invoke()

            assertTrue(result is GenerateProgramResult.Success, "generation should succeed even with a partial lift catalog")
            val program = result.requireProgram()
            val boundFromWorkouts = program.workouts.mapNotNull { it.weekday }.toSet()
            assertEquals(boundFromWorkouts, program.trainingWeekdays)
            val schedule = buildWeeklySchedule(program)
            assertEquals(boundFromWorkouts.size, schedule.filterIsInstance<ScheduleDayItem.WorkoutDay>().size)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-204 generation failure leaves no program")
    inner class FailurePath {
        @Test
        fun `invoke does not persist on generation failure`() = runTest {
            val boom = IllegalArgumentException("boom")
            val useCase =
                buildUseCase(
                    this,
                    contextLoader = FixedProgramGenerationContextLoader(error = { throw boom }),
                )

            val result = useCase.invoke()

            // STR-237 Special 1: the use case records nothing (application
            // has no telemetry capability); the Unexpected branch carries the
            // REAL throwable as a domain diagnostic. The exactly-one
            // exceptional record for this branch is asserted at the outcome
            // consumer's test (CreateProgramGenerateControllerTest), where
            // the record moved.
            val unexpected = result as GenerateProgramResult.Unexpected
            assertEquals(GenerateProgramFailureKind.OperationalFailure, unexpected.kind)
            assertEquals(1, unexpected.diagnostics.size)
            assertSame(boom, unexpected.diagnostics.single().cause)
            assertTrue(programRepository.saveProgramCalls.isEmpty())
            assertNull(programRepository.current)
        }

        @Test
        fun `invoke maps a thrown synthesis exception to SynthesisFailed without an error log`() = runTest {
            val useCase =
                buildUseCase(
                    this,
                    contextLoader =
                        FixedProgramGenerationContextLoader(
                            error = { throw ProgramSynthesisException("quota exhausted") },
                        ),
                )

            val result = useCase.invoke()

            assertEquals(GenerateProgramResult.SynthesisFailed("quota exhausted"), result)
            assertTrue(programRepository.saveProgramCalls.isEmpty())
            assertNull(programRepository.current)
        }
    }

    @Nested
    @DisplayName("Validation rejection maps to the ValidationRejected branch")
    inner class ValidationRejectedPath {
        @Test
        fun `invoke returns ValidationRejected carrying the report`() = runTest {
            val report =
                ValidationReport(
                    pass = false,
                    issues =
                        listOf(
                            ValidationIssue(
                                code = "test",
                                severity = ValidationSeverity.Error,
                                message = "volume too low",
                            ),
                        ),
                    summary =
                        ValidationSummary(
                            weeklyMuscleVolume = emptyMap(),
                            workoutHardSets = emptyList(),
                            estimatedWorkoutMinutes = emptyList(),
                            muscleWeeklyFrequency = emptyMap(),
                            weeklyPushSets = 0.0,
                            weeklyPullSets = 0.0,
                        ),
                )
            val useCase = buildUseCase(this, pipeline = ValidationRejectedPipeline(report))

            val result = useCase.invoke()

            assertEquals(GenerateProgramResult.ValidationRejected(report), result)
            assertTrue(programRepository.saveProgramCalls.isEmpty())
            assertNull(programRepository.current)
        }
    }

    @Nested
    @DisplayName("Synthesis refusal maps to the SynthesisRefused branch")
    inner class SynthesisRefusalPath {
        @Test
        fun `invoke returns SynthesisRefused FeatureDisabled on pipeline refusal`() = runTest {
            val useCase = buildUseCase(this, pipeline = SynthesisRefusedPipeline())

            val result = useCase.invoke()

            assertEquals(
                GenerateProgramResult.SynthesisRefused(ProgramSynthesisRefusal.FeatureDisabled),
                result,
            )
        }
    }

    @Nested
    @DisplayName("PROG-GEN-206 progress callbacks report orchestration phases")
    inner class ProgressPath {
        @Test
        fun `invoke reports pipeline and orchestration phases`() = runTest {
            val phases = mutableListOf<ProgramGenerationProgressPhase>()
            val useCase = buildUseCase(this)

            useCase.invoke { progress -> phases.add(progress.phase) }

            val collapsed =
                phases.fold(mutableListOf<ProgramGenerationProgressPhase>()) { acc, phase ->
                    if (acc.lastOrNull() != phase) acc.add(phase)
                    acc
                }
            assertEquals(
                listOf(
                    ProgramGenerationProgressPhase.PreparingPayload,
                    ProgramGenerationProgressPhase.ReviewingProfile,
                    ProgramGenerationProgressPhase.PlanningVolume,
                    ProgramGenerationProgressPhase.SynthesizingProgram,
                    ProgramGenerationProgressPhase.SelectingExercises,
                    ProgramGenerationProgressPhase.PolishingProgram,
                    ProgramGenerationProgressPhase.FinalizingProgram,
                    ProgramGenerationProgressPhase.Complete,
                ),
                collapsed,
            )
        }
    }

    private lateinit var programRepository: FakeProgramRepository

    private fun GenerateProgramResult.requireProgram(): Program =
        (this as GenerateProgramResult.Success).program

    private fun buildUseCase(
        testScope: TestScope,
        contextLoader: ProgramGenerationContextLoader =
            FixedProgramGenerationContextLoader(
                context = scenarioContext(days = 3, specialization = emptyMap()),
            ),
        enabledLiftIds: Set<LiftId> = SCENARIO_LIFT_CATALOG.map { LiftId(it.id) }.toSet(),
        pipeline: ProgramGenerationPipeline = testProgramGenerationPipeline(),
    ): GenerateProgramUseCase {
        programRepository = FakeProgramRepository()
        return GenerateProgramUseCase(
            loadContext = contextLoader,
            pipeline = pipeline,
            liftRepository = FakeLiftRepository(
                initialLifts =
                    enabledLiftIds.map { id ->
                        com.stratum.domain.lift.Lift(
                            id = id,
                            name = id.value,
                            movementPatterns = emptySet(),
                            primaryMuscles = emptySet(),
                            secondaryMuscles = emptySet(),
                            incrementTiers = emptyList(),
                            presetTags = emptySet(),
                            enabled = true,
                        )
                    },
            ),
            generationDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
        )
    }

    @Test
    @DisplayName("ActivateProgramUseCase persists and appends PROGRAM_REGENERATED")
    fun activateProgramPersistsAndAppendsEvent() = runTest {
        val events = FakeProgramChangeEventRepository()
        val repo = FakeProgramRepository()
        val generate = buildUseCase(this)
        val program = generate.invoke().requireProgram()
        val activate = ActivateProgramUseCase(repo, events)

        assertEquals(ActivateProgramResult.Success, activate(program))

        assertNotNull(repo.current)
        assertTrue(repo.current!!.isActive)
        val appended = events.getEventsForProgram(repo.current!!.id).events
        assertEquals(1, appended.size)
        assertEquals(
            com.stratum.domain.training.ProgramChangeEventType.PROGRAM_REGENERATED,
            appended.single().eventType,
        )
    }

    @Test
    @DisplayName("FPR-S3-006 GenerateProgramUseCase supplies catalog rest lookup")
    fun fprS3006_generateProgramPassesCatalogRestLookup() = runTest {
        // FIX-LIFT-HIGH — High fatigue → 180s rest via catalog lookup
        val highLift =
            Lift(
                id = LiftId("lift-high"),
                name = "Barbell Back Squat",
                movementPatterns = setOf(MovementPattern.SquatPattern),
                primaryMuscles = setOf(MuscleGroup.Quad),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                fatigueProfile = FatigueProfile.High,
                defaultRestSeconds = null,
            )
        // Fake pipeline returns FIX-NORM-ONE-SET for that lift id (not full synthesis path).
        val fixedNormalized =
            NormalizedProgram(
                name = "Rest Matrix Program",
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
                                        liftId = highLift.id.value,
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
            )
        val useCase =
            GenerateProgramUseCase(
                loadContext =
                    FixedProgramGenerationContextLoader(
                        context = scenarioContext(days = 3, specialization = emptyMap()),
                    ),
                pipeline = FixedNormalizedProgramPipeline(fixedNormalized),
                liftRepository = FakeLiftRepository(initialLifts = listOf(highLift)),
                generationDispatcher = UnconfinedTestDispatcher(testScheduler),
            )

        val program = useCase.invoke().requireProgram()

        val highSets =
            program.workouts
                .flatMap { workout -> workout.workoutLifts }
                .filter { lift -> lift.liftId == highLift.id }
                .flatMap { lift -> lift.sets }
        assertTrue(highSets.isNotEmpty(), "expected generated program to retain lift-high")
        // Catalog High → resolvedDefaultRestSeconds 180 once use case passes restSecondsForLift.
        assertTrue(
            highSets.all { set -> set.restSeconds == 180 },
            "expected restSeconds 180 for High fatigue lift, got ${highSets.map { it.restSeconds }}",
        )
    }
}

/**
 * Test double: returns a fixed [NormalizedProgram] so FPR-S3-006 exercises
 * [GenerateProgramUseCase] catalog → toDomainProgram wiring without full synthesis.
 */
private class FixedNormalizedProgramPipeline(
    private val fixed: NormalizedProgram,
) : ProgramGenerationPipeline(
    planWeeklyVolume = PlanWeeklyVolumeUseCase(),
    buildProgramWorkouts = BuildProgramWorkoutsUseCase(),
    integrateAndValidate = IntegrateAndValidateProgramUseCase(),
    programSynthesisPort = FakeProgramSynthesisPort(),
    telemetryLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
) {
    override suspend fun generate(
        context: ProgramGenerationContext,
        normalizationRequest: ProgramNormalizationRequest,
        options: PipelineOptions,
    ): ProgramGenerationPipelineResult = ProgramGenerationPipelineResult.Success(fixed)
}

/**
 * Test double: returns a validation rejection so the rejection-mapping test
 * exercises [GenerateProgramUseCase]'s branch without full synthesis.
 */
private class ValidationRejectedPipeline(
    private val report: ValidationReport,
) : ProgramGenerationPipeline(
    planWeeklyVolume = PlanWeeklyVolumeUseCase(),
    buildProgramWorkouts = BuildProgramWorkoutsUseCase(),
    integrateAndValidate = IntegrateAndValidateProgramUseCase(),
    programSynthesisPort = FakeProgramSynthesisPort(),
    telemetryLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
) {
    override suspend fun generate(
        context: ProgramGenerationContext,
        normalizationRequest: ProgramNormalizationRequest,
        options: PipelineOptions,
    ): ProgramGenerationPipelineResult = ProgramGenerationPipelineResult.ValidationRejected(report)
}

/**
 * Test double: returns a synthesis refusal so the refusal-mapping test exercises
 * [GenerateProgramUseCase]'s branch without full synthesis.
 */
private class SynthesisRefusedPipeline : ProgramGenerationPipeline(
    planWeeklyVolume = PlanWeeklyVolumeUseCase(),
    buildProgramWorkouts = BuildProgramWorkoutsUseCase(),
    integrateAndValidate = IntegrateAndValidateProgramUseCase(),
    programSynthesisPort = FakeProgramSynthesisPort(),
    telemetryLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
) {
    override suspend fun generate(
        context: ProgramGenerationContext,
        normalizationRequest: ProgramNormalizationRequest,
        options: PipelineOptions,
    ): ProgramGenerationPipelineResult =
        ProgramGenerationPipelineResult.SynthesisRefused(ProgramSynthesisRefusal.FeatureDisabled)
}
