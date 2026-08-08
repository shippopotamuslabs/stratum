package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LogSetUseCaseTest {
    @Test
    @DisplayName("WL-031 LogSetUseCase persists set for IN_PROGRESS session")
    fun wl031_logSetInProgress() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        val useCase = buildUseCase(repo)

        val result = useCase(command(sessionId = "ts-live-1"))

        assertEquals(1, repo.insertSetCallCount)
        assertEquals(TrainingSessionId("ts-live-1"), result.getOrThrow().sessionId)
    }

    @Test
    @DisplayName("WL-032 LogSetUseCase fails when session is COMPLETED")
    fun wl032_logSetRejectsCompleted() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.completedLiveSession(id = "ts-done"))
        val logging = FakeLoggingPort()
        val useCase = buildUseCase(repo, logging)

        val result = useCase(command(sessionId = "ts-done"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TrainingSessionNotInProgressException)
        assertEquals(0, repo.insertSetCallCount)
        assertEquals(1, logging.errors.size)
        assertTrue(logging.errors.single().throwable is TrainingSessionNotInProgressException)
    }

    @Test
    @DisplayName("WL-033 Live LoggedLiftContext includes template progression fields")
    fun wl033_liveSnapshotTemplateFields() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        val useCase = buildUseCase(repo)

        val set = useCase(command(workoutLiftId = WorkoutLiftId("wl-1"))).getOrThrow()

        assertEquals(WorkoutLiftId("wl-1"), set.liftContext.workoutLiftId)
        assertEquals(ProgressionScheme.Double, set.liftContext.progressionScheme)
    }

    @Test
    @DisplayName("WL-034 Live snapshot bitmasks match catalog at write time")
    fun wl034_liveSnapshotBitmasks() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        val liftRepo = FakeLiftRepository(initialLifts = listOf(WorkoutLoggingFixtures.benchLift()))
        val useCase = LogSetUseCase(
            repo,
            liftRepo,
            FakeProgramRepository(programWithLift()),
            StubLoggedLiftContextFactory(),
            FakeLoggingPort(),
        )

        val set = useCase(command()).getOrThrow()

        assertEquals(WorkoutLoggingFixtures.CHEST_BITMASK, set.liftContext.primaryMusclesBitmask)
        assertEquals("Barbell Bench Press", set.liftContext.liftName)
    }

    @Test
    @DisplayName("WL-035 session-only workout lift (not on template) still logs with stamped id")
    fun wl035_sessionOnlyWorkoutLiftLogs() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        val sessionOnlyId = WorkoutLiftId("wl-session-added")
        val useCase = buildUseCase(repo)

        val set = useCase(
            command(workoutLiftId = sessionOnlyId).copy(
                setType = SetType.Standard,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
            ),
        ).getOrThrow()

        assertEquals(1, repo.insertSetCallCount)
        assertEquals(sessionOnlyId, set.liftContext.workoutLiftId)
        assertEquals(ProgressionScheme.Double, set.liftContext.progressionScheme)
        assertEquals(SetType.Standard, set.liftContext.setType)
        assertEquals(8, set.liftContext.repMin)
        assertEquals(12, set.liftContext.repMax)
        assertEquals(2, set.liftContext.rirGoal)
        assertEquals(WorkoutLoggingFixtures.CHEST_BITMASK, set.liftContext.primaryMusclesBitmask)
    }

    @Test
    @DisplayName("live command shape preferred over template setIndex for myo minis")
    fun liveCommandShapeOverridesTemplateLookup() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        val useCase = buildUseCase(repo)

        // setIndex 0 would match the template top set, but live mini shape must win.
        val set =
            useCase(
                command(workoutLiftId = WorkoutLiftId("wl-1")).copy(
                    setIndex = 0,
                    setType = SetType.MyoRep,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                ),
            ).getOrThrow()

        assertEquals(SetType.MyoRep, set.liftContext.setType)
        assertEquals(0, set.liftContext.repMin)
        assertEquals(0, set.liftContext.repMax)
        assertEquals(0, set.liftContext.rirGoal)
        assertTrue(set.countsTowardMainBlockFatigue().not())
    }

    @Test
    @DisplayName("WL-036 ad-hoc session with null program still logs overlay lift")
    fun wl036_nullProgramSessionOnlyLift() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(
            WorkoutLoggingFixtures.inProgressSession(
                id = "ts-adhoc",
                workoutId = null,
                kind = TrainingSessionKind.AD_HOC,
            ),
        )
        val sessionOnlyId = WorkoutLiftId("wl-adhoc-1")
        val useCase = LogSetUseCase(
            repo,
            FakeLiftRepository(initialLifts = listOf(WorkoutLoggingFixtures.benchLift())),
            FakeProgramRepository(initial = null),
            StubLoggedLiftContextFactory(),
            FakeLoggingPort(),
        )

        val set = useCase(command(sessionId = "ts-adhoc", workoutLiftId = sessionOnlyId)).getOrThrow()

        assertEquals(1, repo.insertSetCallCount)
        assertEquals(sessionOnlyId, set.liftContext.workoutLiftId)
        assertEquals(ProgressionScheme.Double, set.liftContext.progressionScheme)
    }

    private fun buildUseCase(
        repo: FakeTrainingLogRepository,
        logging: FakeLoggingPort = FakeLoggingPort(),
    ) = LogSetUseCase(
        repo,
        FakeLiftRepository(initialLifts = listOf(WorkoutLoggingFixtures.benchLift())),
        FakeProgramRepository(programWithLift()),
        StubLoggedLiftContextFactory(),
        logging,
    )

    private fun programWithLift() = WorkoutLoggingFixtures.twoWorkoutProgram().copy(
        workouts = listOf(
            WorkoutLoggingFixtures.defaultWorkouts().first().copy(
                workoutLifts = listOf(WorkoutLoggingFixtures.workoutLiftWithDoubleProgression()),
            ),
            WorkoutLoggingFixtures.defaultWorkouts()[1],
        ),
    )

    private fun command(
        sessionId: String = "ts-live-1",
        workoutLiftId: WorkoutLiftId? = WorkoutLiftId("wl-1"),
    ) = LogSetCommand(
        sessionId = TrainingSessionId(sessionId),
        liftId = WorkoutLoggingFixtures.liftBenchId,
        workoutLiftId = workoutLiftId,
        setIndex = 0,
        weight = Weight(135.0, Weight.WeightUnit.Lb),
        reps = 8,
        rir = 2,
        completedAt = Instant.parse("2025-01-01T10:30:00Z"),
    )
}
