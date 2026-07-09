package com.stratum.domain.program

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.program.SCENARIO_LIFT_CATALOG
import com.stratum.core.testing.program.scenarioContext
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.buildWeeklySchedule
import com.stratum.domain.training.ProgramChangeEventRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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

            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
            assertTrue(programRepository.saveProgramCalls.isEmpty())
            assertNull(programRepository.current)
            assertFalse(result.getOrThrow().isActive)
        }

        @Test
        fun `invoke binds training weekdays so weekly schedule has workout days`() = runTest {
            val useCase = buildUseCase(this)

            val program = useCase.invoke().getOrThrow()

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

            assertTrue(result.isSuccess, "generation should succeed even with a partial lift catalog")
            val program = result.getOrThrow()
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
            val useCase = buildUseCase(this, contextLoader = ProgramGenerationContextLoader { error("boom") })

            val result = useCase.invoke()

            assertTrue(result.isFailure)
            assertTrue(programRepository.saveProgramCalls.isEmpty())
            assertNull(programRepository.current)
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

    private fun buildUseCase(
        testScope: TestScope,
        contextLoader: ProgramGenerationContextLoader = ProgramGenerationContextLoader {
            scenarioContext(days = 3, specialization = emptyMap())
        },
        enabledLiftIds: Set<LiftId> = SCENARIO_LIFT_CATALOG.map { LiftId(it.id) }.toSet(),
    ): GenerateProgramUseCase {
        programRepository = FakeProgramRepository()
        return GenerateProgramUseCase(
            loadContext = contextLoader,
            pipeline = testProgramGenerationPipeline(),
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
        val program = generate.invoke().getOrThrow()
        val activate = ActivateProgramUseCase(repo, events)

        activate(program).getOrThrow()

        assertNotNull(repo.current)
        assertTrue(repo.current!!.isActive)
        val appended = events.getEventsForProgram(repo.current!!.id)
        assertEquals(1, appended.size)
        assertEquals(
            com.stratum.domain.training.ProgramChangeEventType.PROGRAM_REGENERATED,
            appended.single().eventType,
        )
    }
}
