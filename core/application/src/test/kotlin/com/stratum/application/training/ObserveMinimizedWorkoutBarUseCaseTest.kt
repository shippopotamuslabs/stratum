package com.stratum.application.training

import app.cash.turbine.test
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.workout.ManageWorkoutOverlayUseCase
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.WorkoutLoggingFixtures.benchLift
import com.stratum.core.testing.training.WorkoutLoggingFixtures.importLiftContext
import com.stratum.core.testing.training.WorkoutLoggingFixtures.inProgressSession
import com.stratum.core.testing.training.WorkoutLoggingFixtures.loggedSet
import com.stratum.core.testing.training.WorkoutLoggingFixtures.twoWorkoutProgram
import com.stratum.core.testing.training.WorkoutLoggingFixtures.workoutLiftWithDoubleProgression
import com.stratum.core.testing.workout.FakeWorkoutOverlayStore
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [ObserveMinimizedWorkoutBarUseCase] bar-assembly tests (STR-210): the minimized-bar
 * composition moved out of `ProgramsViewModel` must preserve the VM's prior behavior —
 * program/ad-hoc/rest/orphan/null-session shapes, next-lift resolution, and the soft-fail
 * fallback labels.
 */
class ObserveMinimizedWorkoutBarUseCaseTest {

    @Test
    @DisplayName("no active session emits null bar")
    fun noActiveSessionEmitsNull() = runTest {
        val subject = buildUseCase(program = twoWorkoutProgram(), trainingLog = FakeTrainingLogRepository())

        subject().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("active program session emits bar with template name and next lift")
    fun activeProgramSessionEmitsBar() = runTest {
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(inProgressSession(id = "ts-live-1"))
        val subject = buildUseCase(program = programWithBenchLift(), trainingLog = trainingLog)

        subject().test {
            val bar = awaitItem()
            assertTrue(bar != null)
            assertEquals(WorkoutLoggingFixtures.WORKOUT_A_ID, bar?.workoutId)
            assertEquals("Workout A", bar?.workoutName)
            assertEquals("Barbell Bench Press", bar?.nextLiftName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("fully logged template emits Workout in progress fallback")
    fun fullyLoggedTemplateEmitsProgressFallback() = runTest {
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(inProgressSession(id = "ts-live-1"))
        trainingLog.seedSet(
            loggedSet(
                id = "ls-log-1",
                sessionId = TrainingSessionId("ts-live-1"),
                liftContext = importLiftContext().copy(workoutLiftId = workoutLiftWithDoubleProgression().id),
            ),
        )
        val subject = buildUseCase(program = programWithBenchLift(), trainingLog = trainingLog)

        subject().test {
            val bar = awaitItem()
            assertEquals("Workout in progress", bar?.nextLiftName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("ad-hoc session emits bar with session route id and Add lifts")
    fun adHocSessionEmitsBarWithSessionRoute() = runTest {
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(
            inProgressSession(id = "ts-adhoc-1", workoutId = null, kind = TrainingSessionKind.AD_HOC)
                .copy(workoutNameSnapshot = "Empty Workout"),
        )
        val subject = buildUseCase(program = twoWorkoutProgram(), trainingLog = trainingLog)

        subject().test {
            val bar = awaitItem()
            assertEquals("ts-adhoc-1", bar?.workoutId)
            assertEquals("Empty Workout", bar?.workoutName)
            assertEquals("Add lifts", bar?.nextLiftName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("orphaned program session emits null so the bar hides")
    fun orphanedSessionEmitsNull() = runTest {
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(inProgressSession(id = "ts-orphan", workoutId = WorkoutId("deleted-w")))
        val subject = buildUseCase(program = programWithBenchLift(), trainingLog = trainingLog)

        subject().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("rest-day session emits null")
    fun restDaySessionEmitsNull() = runTest {
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(
            inProgressSession(id = "ts-rest", workoutId = null, kind = TrainingSessionKind.REST_DAY),
        )
        val subject = buildUseCase(program = twoWorkoutProgram(), trainingLog = trainingLog)

        subject().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun programWithBenchLift() =
        twoWorkoutProgram(
            workouts =
                listOf(
                    Workout(
                        id = WorkoutLoggingFixtures.workoutAId,
                        programId = WorkoutLoggingFixtures.programId,
                        name = "Workout A",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts = listOf(workoutLiftWithDoubleProgression()),
                    ),
                ),
        )

    private fun buildUseCase(
        program: Program?,
        trainingLog: FakeTrainingLogRepository,
    ): ObserveMinimizedWorkoutBarUseCase {
        val repository = FakeProgramRepository(program)
        val liftRepository = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val overlayStore = FakeWorkoutOverlayStore()
        return ObserveMinimizedWorkoutBarUseCase(
            ObserveCycleProgressUseCase(repository, trainingLog),
            ObserveLiftCatalogUseCase(liftRepository),
            ManageWorkoutOverlayUseCase(overlayStore),
            GetLoggedSetsForSessionUseCase(trainingLog),
        )
    }
}
