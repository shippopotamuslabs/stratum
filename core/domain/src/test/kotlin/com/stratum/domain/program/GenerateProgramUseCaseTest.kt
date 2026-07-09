package com.stratum.domain.program

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.core.testing.program.SCENARIO_LIFT_CATALOG
import com.stratum.core.testing.program.scenarioContext
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.buildWeeklySchedule
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [GenerateProgramUseCase] orchestration tests. Scenarios PROG-GEN-100, PROG-GEN-204, PROG-GEN-205.
 */
class GenerateProgramUseCaseTest {
    @Nested
    @DisplayName("PROG-GEN-100 happy path persists program and completes onboarding")
    inner class HappyPath {
        @Test
        fun `invoke persists a generated program and completes onboarding`() = runTest {
            val useCase = buildUseCase(this)

            val result = useCase.invoke()

            assertTrue(result.isSuccess)
            assertTrue(programRepository.current != null)
            assertTrue(programRepository.saveProgramCalls.isNotEmpty())
            assertTrue(onboardingRepository.current.isComplete)
            assertEquals(SEEDED_PROFILE, profileRepository.current)
        }

        @Test
        fun `invoke binds training weekdays so weekly schedule has workout days`() = runTest {
            val useCase = buildUseCase(this)

            useCase.invoke().getOrThrow()

            val program = programRepository.current!!
            val workoutDays =
                buildWeeklySchedule(program).filterIsInstance<ScheduleDayItem.WorkoutDay>()
            assertTrue(workoutDays.isNotEmpty(), "expected workout days in weekly schedule")
            assertEquals(program.workouts.size, workoutDays.size)
            assertTrue(program.trainingWeekdays.isNotEmpty())
            // trainingWeekdays is a denormalized cache of Workout.weekday — the single source of
            // truth. The two must always agree after generation.
            assertEquals(
                program.workouts.mapNotNull { it.weekday }.toSet(),
                program.trainingWeekdays,
            )
        }

        @Test
        @DisplayName("retainOnlyKnownLifts dropping workouts keeps trainingWeekdays consistent with survivors")
        fun `invoke keeps training weekdays consistent when sanitization drops workouts`() = runTest {
            // Seed only upper-body lifts: lower-dominant workouts lose all their lifts to
            // retainOnlyKnownLifts and are dropped, so the surviving trainingWeekdays must
            // re-derive from the workouts that remain — no orphan weekdays, no crash.
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

            assertTrue(result.isSuccess, "generation should succeed even with a partial lift catalog")
            val program = programRepository.current!!
            // Invariant: trainingWeekdays equals the set of bound workout weekdays among survivors.
            val boundFromWorkouts = program.workouts.mapNotNull { it.weekday }.toSet()
            assertEquals(boundFromWorkouts, program.trainingWeekdays)
            // buildWeeklySchedule must not crash and must show exactly the surviving workout days.
            val schedule = buildWeeklySchedule(program)
            assertEquals(boundFromWorkouts.size, schedule.filterIsInstance<ScheduleDayItem.WorkoutDay>().size)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-204 generation failure leaves no program and no completion")
    inner class FailurePath {
        @Test
        fun `invoke does not persist or complete on generation failure`() = runTest {
            val useCase = buildUseCase(this, contextLoader = ProgramGenerationContextLoader { error("boom") })

            val result = useCase.invoke()

            assertTrue(result.isFailure)
            assertTrue(programRepository.saveProgramCalls.isEmpty())
            assertEquals(null, programRepository.current)
            assertFalse(onboardingRepository.current.isComplete)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-205 persist failure leaves onboarding incomplete")
    inner class PersistFailurePath {
        @Test
        fun `invoke does not complete onboarding when saveProgram fails`() = runTest {
            val useCase = buildUseCase(this, repositoryFailOnSave = true)

            val result = useCase.invoke()

            assertTrue(result.isFailure)
            assertEquals(null, programRepository.current)
            assertFalse(onboardingRepository.current.isComplete)
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
                    ProgramGenerationProgressPhase.SavingProgram,
                    ProgramGenerationProgressPhase.Complete,
                ),
                collapsed,
            )
        }
    }

    private lateinit var programRepository: FakeProgramRepository
    private lateinit var onboardingRepository: FakeOnboardingRepository
    private lateinit var profileRepository: FakeUserTrainingProfileRepository

    private fun buildUseCase(
        testScope: TestScope,
        events: ProgramChangeEventRepository = NoOpProgramChangeEventRepository(),
        contextLoader: ProgramGenerationContextLoader = ProgramGenerationContextLoader {
            scenarioContext(days = 3, specialization = emptyMap())
        },
        repositoryFailOnSave: Boolean = false,
        enabledLiftIds: Set<LiftId> = SCENARIO_LIFT_CATALOG.map { LiftId(it.id) }.toSet(),
    ): GenerateProgramUseCase {
        programRepository = FakeProgramRepository(failOnSave = repositoryFailOnSave)
        onboardingRepository = FakeOnboardingRepository()
        profileRepository = FakeUserTrainingProfileRepository(initial = SEEDED_PROFILE)
        return GenerateProgramUseCase(
            loadContext = contextLoader,
            pipeline = testProgramGenerationPipeline(),
            repository = programRepository,
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
            completeOnboarding = CompleteOnboardingUseCase(onboardingRepository, profileRepository),
            profileRepository = profileRepository,
            programChangeEventRepository = events,
            generationDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
        )
    }

    private val SEEDED_PROFILE = UserTrainingProfile.Empty.copy(trainingAge = TrainingAge.Advanced)

    @Test
    @DisplayName("WL-037 GenerateProgramUseCase appends PROGRAM_REGENERATED event")
    fun wl037_generateProgramAppendsRegeneratedEvent() = runTest {
        val events = com.stratum.core.testing.training.FakeProgramChangeEventRepository()
        val useCase = buildUseCase(this, events)

        useCase.invoke().getOrThrow()

        val appended = events.getEventsForProgram(programRepository.current!!.id)
        assertEquals(1, appended.size)
        assertEquals(
            com.stratum.domain.training.ProgramChangeEventType.PROGRAM_REGENERATED,
            appended.single().eventType,
        )
    }
}
