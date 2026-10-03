package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetWorkoutSessionDetailUseCaseTest {

    @Test
    @DisplayName("Prefers workoutNameSnapshot over live program name")
    fun prefersSnapshotName() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-1").copy(
                workoutNameSnapshot = "Snap Upper",
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                initialSets =
                    listOf(
                        LoggedSet(
                            id = LoggedSetId("set-snap"),
                            sessionId = TrainingSessionId("ts-1"),
                            exerciseNameRaw = "Bench",
                            liftId = WorkoutLoggingFixtures.liftBenchId,
                            setIndex = 0,
                            weight = Weight(135.0, Weight.WeightUnit.Lb),
                            reps = 8,
                            rir = 2,
                            completedAt = Instant.parse("2025-01-01T10:10:00Z"),
                            liftContext = WorkoutLoggingFixtures.importLiftContext(),
                        ),
                    ),
                programRepository = programRepo,
            )
        val useCase = detailUseCase(logRepo, programRepo)

        val detail = useCase(TrainingSessionId("ts-1")).getOrThrow()
        assertEquals("Snap Upper", detail.workoutName)
    }

    @Test
    @DisplayName("Groups sets by lift with PR count and duration")
    fun groupsSetsAndCountsPrs() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                programRepository = programRepo,
            )
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:10:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
                isPersonalRecord = true,
            ),
        )
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-2"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 1,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 6,
                rir = 1,
                completedAt = Instant.parse("2025-01-01T10:20:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            ),
        )
        val useCase = detailUseCase(logRepo, programRepo)
        val detail = useCase(TrainingSessionId("ts-1")).getOrThrow()

        assertEquals(3_600_000L, detail.durationMillis)
        assertEquals(2, detail.totalSets)
        assertEquals(1, detail.personalRecordCount)
        assertEquals(1, detail.lifts.size)
        assertEquals(2, detail.lifts.single().sets.size)
        assertTrue(detail.lifts.single().sets.first().isPersonalRecord)
        assertEquals(0, detail.lifts.single().bestSet?.setIndex)
        assertTrue(detail.lifts.single().bestSet?.isPersonalRecord == true)
        assertTrue(detail.muscleVolumes.isNotEmpty())
    }

    @Test
    @DisplayName("Missing session fails")
    fun missingSessionFails() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val logRepo = FakeTrainingLogRepository(programRepository = programRepo)
        val logging = FakeLoggingPort()
        val useCase = detailUseCase(logRepo, programRepo, logging)
        assertTrue(useCase(TrainingSessionId("missing")).isFailure)
        assertExpectedFailureLogged(logging)
    }

    @Test
    @DisplayName("Zero-set completed session fails (skips are not history detail)")
    fun zeroSetSessionFails() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-skip")
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                programRepository = programRepo,
            )
        val logging = FakeLoggingPort()
        val useCase = detailUseCase(logRepo, programRepo, logging)
        assertTrue(useCase(TrainingSessionId("ts-skip")).isFailure)
        assertExpectedFailureLogged(logging)
    }

    @Test
    @DisplayName("In-progress session fails")
    fun inProgressFails() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.inProgressSession(id = "ts-ip")
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                initialSets =
                    listOf(
                        LoggedSet(
                            id = LoggedSetId("set-1"),
                            sessionId = TrainingSessionId("ts-ip"),
                            exerciseNameRaw = "Bench",
                            liftId = WorkoutLoggingFixtures.liftBenchId,
                            setIndex = 0,
                            weight = Weight(135.0, Weight.WeightUnit.Lb),
                            reps = 8,
                            rir = 2,
                            completedAt = Instant.parse("2025-01-01T10:10:00Z"),
                            liftContext = WorkoutLoggingFixtures.importLiftContext(),
                        ),
                    ),
                programRepository = programRepo,
            )
        val logging = FakeLoggingPort()
        val useCase = detailUseCase(logRepo, programRepo, logging)
        assertTrue(useCase(TrainingSessionId("ts-ip")).isFailure)
        assertExpectedFailureLogged(logging)
    }

    private fun detailUseCase(
        logRepo: FakeTrainingLogRepository,
        programRepo: FakeProgramRepository,
        logging: FakeLoggingPort = FakeLoggingPort(),
    ) = GetWorkoutSessionDetailUseCase(
        trainingLogRepository = logRepo,
        programRepository = programRepo,
        computeLoggedSessionMuscleVolume = ComputeLoggedSessionMuscleVolumeUseCase(),
        loggingPort = logging,
    )

    private fun assertExpectedFailureLogged(logging: FakeLoggingPort) {
        // Expected business outcomes must leave a breadcrumb only — never a Crashlytics non-fatal.
        assertEquals(0, logging.errors.size)
        assertEquals(1, logging.warnings.size)
    }
}
