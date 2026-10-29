package com.stratum.application.training

import com.stratum.core.testing.healthconnect.FakeHealthConnectSchedulerPort
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.training.LoggedSet

class UpdateCompletedSessionTimesUseCaseTest {

    @Test
    @DisplayName("Updates startedAt and endedAt and shifts set timestamps")
    fun updatesTimesAndShiftsSetsSuccessfully() = runTest {
        val session =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-1").copy(
                startedAt = Instant.parse("2025-06-01T10:00:00Z"),
                endedAt = Instant.parse("2025-06-01T11:00:00Z"),
            )
        val set1 =
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-06-01T10:15:00Z"), // 25% through
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            )
        val set2 =
            LoggedSet(
                id = LoggedSetId("set-2"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 1,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-06-01T10:45:00Z"), // 75% through
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                initialSets = listOf(set1, set2),
            )
        val fakeHcScheduler = FakeHealthConnectSchedulerPort()
        val fakeModelingScheduler = FakeResponseModelingSchedulerPort()
        val fakeMetadataRepo = FakeResponseProfileMetadataRepository()
        val useCase = buildUseCase(
            logRepo = logRepo,
            hcScheduler = fakeHcScheduler,
            modelingScheduler = fakeModelingScheduler,
            metadataRepo = fakeMetadataRepo,
        )

        val newStart = Instant.parse("2025-06-03T14:00:00Z")
        val newEnd = Instant.parse("2025-06-03T14:30:00Z") // 30 min duration

        val result = useCase(TrainingSessionId("ts-1"), newStart, newEnd)
        assertTrue(result.isSuccess)

        val updated = logRepo.getSession(TrainingSessionId("ts-1"))!!
        assertEquals(newStart, updated.startedAt)
        assertEquals(newEnd, updated.endedAt)

        val updatedSets = logRepo.getSetsForSession(TrainingSessionId("ts-1"))
        assertEquals(2, updatedSets.size)
        // 25% of 30 min is 7.5 min -> 14:07:30
        assertEquals(Instant.parse("2025-06-03T14:07:30Z"), updatedSets[0].completedAt)
        // 75% of 30 min is 22.5 min -> 14:22:30
        assertEquals(Instant.parse("2025-06-03T14:22:30Z"), updatedSets[1].completedAt)

        assertEquals(listOf(TrainingSessionId("ts-1")), fakeHcScheduler.enqueuedSessionIds)
    }

    @Test
    @DisplayName("Distributes sets evenly when all sets have identical original timestamps")
    fun distributesEvenlyWhenOriginalSetsClustered() = runTest {
        val session =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-clustered").copy(
                startedAt = Instant.parse("2025-06-01T10:00:00Z"),
                endedAt = Instant.parse("2025-06-01T11:00:00Z"),
            )
        val set1 =
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("ts-clustered"),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-06-01T11:00:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            )
        val set2 =
            LoggedSet(
                id = LoggedSetId("set-2"),
                sessionId = TrainingSessionId("ts-clustered"),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 1,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-06-01T11:00:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                initialSets = listOf(set1, set2),
            )
        val useCase = buildUseCase(logRepo)

        val newStart = Instant.parse("2025-06-03T14:00:00Z")
        val newEnd = Instant.parse("2025-06-03T15:00:00Z")

        val result = useCase(TrainingSessionId("ts-clustered"), newStart, newEnd)
        assertTrue(result.isSuccess)

        val updatedSets = logRepo.getSetsForSession(TrainingSessionId("ts-clustered"))
        assertEquals(Instant.parse("2025-06-03T14:00:00Z"), updatedSets[0].completedAt)
        assertEquals(Instant.parse("2025-06-03T15:00:00Z"), updatedSets[1].completedAt)
    }

    @Test
    @DisplayName("Triggers dose-response force extract when session was in last modeling run")
    fun enqueuesModelingForceExtract() = runTest {
        val session =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-modeled").copy(
                startedAt = Instant.parse("2025-06-01T10:00:00Z"),
                endedAt = Instant.parse("2025-06-01T11:00:00Z"),
            )
        val logRepo = FakeTrainingLogRepository(initialSessions = listOf(session))
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(
            ResponseProfileMetadata(
                lastExtractedAt = Instant.parse("2025-06-02T00:00:00Z"),
                epochsAnalyzed = 5,
                analysisWindowStart = Instant.parse("2025-05-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-02T00:00:00Z"),
            ),
        )
        val modelingScheduler = FakeResponseModelingSchedulerPort()
        val useCase = buildUseCase(
            logRepo = logRepo,
            metadataRepo = metadataRepo,
            modelingScheduler = modelingScheduler,
        )

        val result = useCase(
            TrainingSessionId("ts-modeled"),
            Instant.parse("2025-06-05T10:00:00Z"),
            Instant.parse("2025-06-05T11:00:00Z"),
        )
        assertTrue(result.isSuccess)
        assertEquals(1, modelingScheduler.forceExtractCount)
        assertEquals(true, modelingScheduler.lastForceReplaceExisting)
    }

    @Test
    @DisplayName("Health Connect sync failure is resilient and does not fail use case")
    fun healthConnectFailureIsResilient() = runTest {
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo = FakeTrainingLogRepository(initialSessions = listOf(session))
        val hcScheduler = FakeHealthConnectSchedulerPort()
        hcScheduler.throwOnEnqueue = RuntimeException("HC Unavailable")
        val useCase = buildUseCase(logRepo = logRepo, hcScheduler = hcScheduler)

        val result = useCase(
            TrainingSessionId("ts-1"),
            Instant.parse("2025-06-01T14:00:00Z"),
            Instant.parse("2025-06-01T15:00:00Z"),
        )
        assertTrue(result.isSuccess)
    }

    @Test
    @DisplayName("Fails when session does not exist")
    fun missingSessionFails() = runTest {
        val logRepo = FakeTrainingLogRepository()
        val useCase = buildUseCase(logRepo)

        val result = useCase(
            TrainingSessionId("missing"),
            Instant.parse("2025-06-01T14:00:00Z"),
            Instant.parse("2025-06-01T15:00:00Z"),
        )
        assertTrue(result.isFailure)
    }

    @Test
    @DisplayName("Fails when session is in progress")
    fun inProgressSessionFails() = runTest {
        val session = WorkoutLoggingFixtures.inProgressSession(id = "ts-ip")
        val logRepo = FakeTrainingLogRepository(initialSessions = listOf(session))
        val useCase = buildUseCase(logRepo)

        val result = useCase(
            TrainingSessionId("ts-ip"),
            Instant.parse("2025-06-01T14:00:00Z"),
            Instant.parse("2025-06-01T15:00:00Z"),
        )
        assertTrue(result.isFailure)
    }

    @Test
    @DisplayName("Fails when startedAt is after endedAt")
    fun startAfterEndFails() = runTest {
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo = FakeTrainingLogRepository(initialSessions = listOf(session))
        val useCase = buildUseCase(logRepo)

        val start = Instant.parse("2025-06-01T16:00:00Z")
        val end = Instant.parse("2025-06-01T15:00:00Z")

        val result = useCase(TrainingSessionId("ts-1"), start, end)
        assertTrue(result.isFailure)
    }

    private fun buildUseCase(
        logRepo: FakeTrainingLogRepository,
        hcScheduler: FakeHealthConnectSchedulerPort = FakeHealthConnectSchedulerPort(),
        metadataRepo: FakeResponseProfileMetadataRepository = FakeResponseProfileMetadataRepository(),
        modelingScheduler: FakeResponseModelingSchedulerPort = FakeResponseModelingSchedulerPort(),
    ): UpdateCompletedSessionTimesUseCase {
        val liftRepo = FakeLiftRepository(listOf(WorkoutLoggingFixtures.benchLift()))
        val persistence = FakeResponseProfilePersistencePort(metadataRepository = metadataRepo)
        return UpdateCompletedSessionTimesUseCase(
            trainingLogRepository = logRepo,
            localDerivedStages =
                com.stratum.core.testing.training.localDerivedTrainingStages(
                    trainingLog = logRepo,
                    liftRepository = liftRepo,
                    persistence = persistence,
                ),
            responseProfileMetadataRepository = metadataRepo,
            responseModelingScheduler = modelingScheduler,
            healthConnectScheduler = hcScheduler,
            loggingPort = FakeLoggingPort(),
            refreshScope = CoroutineScope(Dispatchers.Unconfined),
        )
    }
}
