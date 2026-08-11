package com.stratum.domain.training

import com.stratum.core.testing.healthconnect.FakeHealthConnectSchedulerPort
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.localDerivedTrainingStages
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FinalizeTrainingSessionUseCaseTest {
    @Test
    @DisplayName("WL-023 FinalizeTrainingSessionUseCase marks session COMPLETED")
    fun wl023_finalizeMarksCompleted() = runTest {
        val useCase = buildUseCase()

        val result = useCase(TrainingSessionId("ts-live-1"))

        val session = result.getOrThrow()
        assertEquals(TrainingSessionStatus.COMPLETED, session.status)
        assertNotNull(session.endedAt)
    }

    @Test
    @DisplayName("WL-024 Finalize program workout increments cycle_position cache")
    fun wl024_finalizeAdvancesCyclePosition() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 0))
        val useCase = buildUseCase(programRepo)

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        assertEquals(1, programRepo.getProgram()?.cyclePosition)
    }

    @Test
    @DisplayName("WL-025 Finalize AD_HOC session does not change cycle pointer")
    fun wl025_adHocFinalizeNoAdvance() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 0))
        val useCase = buildUseCase(programRepo)

        useCase(TrainingSessionId("ts-adhoc")).getOrThrow()

        assertEquals(0, programRepo.getProgram()?.cyclePosition)
        assertEquals(1, programRepo.getProgram()?.currentCycleNumber)
    }

    @Test
    @DisplayName("WL-039 Finalizing a single workout does not append program_change_events")
    fun wl039_finalizeDoesNotEmitChangeEvent() = runTest {
        val events = FakeProgramChangeEventRepository()
        val useCase = buildUseCase(eventRepository = events)

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        assertEquals(0, events.getEventsForProgram(WorkoutLoggingFixtures.programId).size)
    }

    @Test
    @DisplayName("Finalizing session triggers Health Connect sync enqueue")
    fun finalizeTriggersHealthConnectSync() = runTest {
        val scheduler = FakeHealthConnectSchedulerPort()

        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val events = FakeProgramChangeEventRepository()
        val repo = FakeTrainingLogRepository(programRepository = programRepo, programChangeEventRepository = events)
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-hc"))

        val computePersonalRecords = ComputePersonalRecordsUseCase(repo)
        val useCase = FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            computePersonalRecords = computePersonalRecords,
            localDerivedStages = localDerivedTrainingStages(
                trainingLog = repo,
                extractTrainingInsights = buildInsightsComponents(repo).useCase,
                refreshWeeklySeries = RefreshWeeklySeriesUseCase(repo, FakeLiftRepository(), FakeResponseProfilePersistencePort()),
            ),
            loggingPort = FakeLoggingPort(),
            healthConnectScheduler = scheduler,
            refreshScope = CoroutineScope(Dispatchers.Unconfined),
        )

        useCase(TrainingSessionId("ts-live-hc")).getOrThrow()

        assertEquals(1, scheduler.enqueuedSessionIds.size)
        assertEquals(TrainingSessionId("ts-live-hc"), scheduler.enqueuedSessionIds.first())
    }

    @Test
    @DisplayName("Finalize still succeeds when Health Connect enqueue throws")
    fun finalizeSucceedsWhenHealthConnectEnqueueThrows() = runTest {
        val scheduler = FakeHealthConnectSchedulerPort().apply {
            throwOnEnqueue = IllegalStateException("DataStore boom")
        }

        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val events = FakeProgramChangeEventRepository()
        val repo = FakeTrainingLogRepository(programRepository = programRepo, programChangeEventRepository = events)
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-hc-fail"))

        val computePersonalRecords = ComputePersonalRecordsUseCase(repo)
        val useCase = FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            computePersonalRecords = computePersonalRecords,
            localDerivedStages = localDerivedTrainingStages(
                trainingLog = repo,
                extractTrainingInsights = buildInsightsComponents(repo).useCase,
                refreshWeeklySeries = RefreshWeeklySeriesUseCase(repo, FakeLiftRepository(), FakeResponseProfilePersistencePort()),
            ),
            loggingPort = FakeLoggingPort(),
            healthConnectScheduler = scheduler,
            refreshScope = CoroutineScope(Dispatchers.Unconfined),
        )

        val result = useCase(TrainingSessionId("ts-live-hc-fail"))

        assertTrue(result.isSuccess)
        assertEquals(TrainingSessionStatus.COMPLETED, result.getOrThrow().status)
    }

    @Test
    @DisplayName("Finalize updates sets in session with computed e1RMs and PRs")
    fun finalizeUpdatesSetsWithPrs() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val events = FakeProgramChangeEventRepository()
        val repo = FakeTrainingLogRepository(programRepository = programRepo, programChangeEventRepository = events)
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))

        val liftId = com.stratum.domain.lift.identity.LiftId("lift-1")
        val set1 = LoggedSet(
            id = LoggedSetId("set-1"),
            sessionId = TrainingSessionId("ts-live-1"),
            exerciseNameRaw = "Squat",
            liftId = liftId,
            setIndex = 1,
            weight = com.stratum.domain.lift.Weight(100.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
            reps = 5,
            rir = null,
            completedAt = Instant.now(),
            liftContext = LoggedLiftContext("Squat")
        )
        repo.seedSet(set1)

        val computePersonalRecords = ComputePersonalRecordsUseCase(repo)
        val insightsComponents = buildInsightsComponents(repo)
        val persistence = FakeResponseProfilePersistencePort()
        val useCase = FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            computePersonalRecords = computePersonalRecords,
            localDerivedStages = localDerivedTrainingStages(
                trainingLog = repo,
                extractTrainingInsights = insightsComponents.useCase,
                refreshWeeklySeries = RefreshWeeklySeriesUseCase(repo, FakeLiftRepository(), persistence),
            ),
            loggingPort = FakeLoggingPort(),
            refreshScope = CoroutineScope(Dispatchers.Unconfined),
        )

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        val updatedSets = repo.getSetsForSession(TrainingSessionId("ts-live-1"))
        assertEquals(1, updatedSets.size)
        val updatedSet = updatedSets.first()
        assertTrue(updatedSet.isPersonalRecord)
        assertEquals(100.0 * (1.0278 / (1.0278 - 0.0278 * 5)), updatedSet.estimatedOneRm!!, 0.01)
    }

    @Test
    @DisplayName("Finalize demotes prior PR then promotes session PR without unique conflict")
    fun finalizeDemotesOldPrBeforePromotingNew() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val events = FakeProgramChangeEventRepository()
        val repo =
            FakeTrainingLogRepository(
                programRepository = programRepo,
                programChangeEventRepository = events,
            )
        val liftId = com.stratum.domain.lift.identity.LiftId("lift-1")
        val priorSession = WorkoutLoggingFixtures.completedLiveSession(id = "ts-prior")
        repo.seedSession(priorSession)
        repo.seedSet(
            LoggedSet(
                id = LoggedSetId("old-pr"),
                sessionId = priorSession.id,
                exerciseNameRaw = "Squat",
                liftId = liftId,
                setIndex = 0,
                weight =
                    com.stratum.domain.lift.Weight(
                        100.0,
                        com.stratum.domain.lift.Weight.WeightUnit.Lb,
                    ),
                reps = 5,
                rir = null,
                completedAt = Instant.parse("2025-01-01T09:00:00Z"),
                liftContext = LoggedLiftContext("Squat"),
                isPersonalRecord = true,
                estimatedOneRm = 112.5,
            ),
        )
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        repo.seedSet(
            LoggedSet(
                id = LoggedSetId("new-pr"),
                sessionId = TrainingSessionId("ts-live-1"),
                exerciseNameRaw = "Squat",
                liftId = liftId,
                setIndex = 0,
                weight =
                    com.stratum.domain.lift.Weight(
                        135.0,
                        com.stratum.domain.lift.Weight.WeightUnit.Lb,
                    ),
                reps = 5,
                rir = null,
                completedAt = Instant.now(),
                liftContext = LoggedLiftContext("Squat"),
            ),
        )
        val insights = buildInsightsComponents(repo)
        val persistence = FakeResponseProfilePersistencePort()
        val useCase =
            FinalizeTrainingSessionUseCase(
                trainingLogRepository = repo,
                programRepository = programRepo,
                computePersonalRecords = ComputePersonalRecordsUseCase(repo),
                localDerivedStages =
                    localDerivedTrainingStages(
                        trainingLog = repo,
                        extractTrainingInsights = insights.useCase,
                        refreshWeeklySeries =
                            RefreshWeeklySeriesUseCase(repo, FakeLiftRepository(), persistence),
                    ),
                loggingPort = FakeLoggingPort(),
                refreshScope = CoroutineScope(Dispatchers.Unconfined),
            )

        val result = useCase(TrainingSessionId("ts-live-1"))
        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertTrue(
            repo.getSetsForSession(TrainingSessionId("ts-live-1")).single().isPersonalRecord,
        )
        assertFalse(repo.getSetsForSession(priorSession.id).single().isPersonalRecord)
    }

    @Test
    @DisplayName("Finalize returns before the refresh completes (non-blocking)")
    fun finalizeIsNonBlocking() = runTest(StandardTestDispatcher()) {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val events = FakeProgramChangeEventRepository()
        val repo = FakeTrainingLogRepository(
            programRepository = programRepo,
            programChangeEventRepository = events,
        )
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        val insights = buildInsightsComponents(repo)
        val persistence = FakeResponseProfilePersistencePort()
        val modelingScheduler = FakeResponseModelingSchedulerPort()
        // StandardTestDispatcher does NOT run eagerly — launched coroutines queue until the
        // scheduler advances. Both the scope and the dispatcher use the test scheduler so we can
        // prove invoke() returns before the refresh runs.
        val refreshScope = CoroutineScope(coroutineContext)
        val useCase = FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            computePersonalRecords = ComputePersonalRecordsUseCase(repo),
            localDerivedStages = localDerivedTrainingStages(
                trainingLog = repo,
                extractTrainingInsights = insights.useCase,
                refreshWeeklySeries = RefreshWeeklySeriesUseCase(repo, FakeLiftRepository(), persistence),
            ),
            loggingPort = FakeLoggingPort(),
            responseModelingScheduler = modelingScheduler,
            refreshScope = refreshScope,
        )

        // invoke() must return before the refresh runs (dispatcher hasn't advanced).
        val result = useCase(TrainingSessionId("ts-live-1"))
        assertTrue(result.isSuccess)
        assertEquals(TrainingSessionStatus.COMPLETED, result.getOrThrow().status)
        assertTrue(insights.metadataRepo.saved == null, "refresh must not have run before the scheduler advances")
        // Modeling enqueue is synchronous schedule-only (not on refreshDispatcher) and must not block.
        assertEquals(1, modelingScheduler.enqueueCount, "dose-response work enqueued without awaiting")

        // Advance the dispatcher — the refresh now runs. No sets were seeded so the weekly series
        // use case no-ops, but the rollup refresh still persists metadata.
        advanceUntilIdle()
        assertTrue(insights.metadataRepo.saved != null, "insights metadata persisted after refresh runs")
    }

    @Test
    @DisplayName("Finalize succeeds even when insights refresh fails")
    fun finalizeSucceedsWhenInsightsRefreshFails() = runTest(UnconfinedTestDispatcher()) {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val events = FakeProgramChangeEventRepository()
        val repo = FakeTrainingLogRepository(
            programRepository = programRepo,
            programChangeEventRepository = events,
        )
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        val failingUseCase = ExtractTrainingInsightsUseCase(
            trainingLogRepository = repo,
            liftTrainingInsightsRepository = NoopLiftInsightsRepository(),
            supersetAffinityRepository = NoopSupersetRepository(),
            muscleVolumeBaselineRepository = NoopVolumeRepository(),
            importAnalysisMetadataRepository = ThrowingMetadataRepository(),
            liftRepository = FakeLiftRepository(),
        )
        val persistence = FakeResponseProfilePersistencePort()
        val useCase = FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            computePersonalRecords = ComputePersonalRecordsUseCase(repo),
            localDerivedStages = localDerivedTrainingStages(
                trainingLog = repo,
                extractTrainingInsights = failingUseCase,
                refreshWeeklySeries = RefreshWeeklySeriesUseCase(repo, FakeLiftRepository(), persistence),
            ),
            loggingPort = FakeLoggingPort(),
            refreshScope = CoroutineScope(Dispatchers.Unconfined),
        )

        val result = useCase(TrainingSessionId("ts-live-1"))

        assertTrue(result.isSuccess, "finalize must succeed even if insights refresh fails")
        assertEquals(TrainingSessionStatus.COMPLETED, result.getOrThrow().status)
    }

    private suspend fun buildUseCase(
        programRepo: FakeProgramRepository = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram()),
        eventRepository: FakeProgramChangeEventRepository = FakeProgramChangeEventRepository(),
    ): FinalizeTrainingSessionUseCase {
        val repo =
            FakeTrainingLogRepository(
                programRepository = programRepo,
                programChangeEventRepository = eventRepository,
            )
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        repo.seedSession(
            WorkoutLoggingFixtures.inProgressSession(
                id = "ts-adhoc",
                workoutId = null,
                kind = TrainingSessionKind.AD_HOC,
            ),
        )
        val computePersonalRecords = ComputePersonalRecordsUseCase(repo)
        val insights = buildInsightsComponents(repo)
        val persistence = FakeResponseProfilePersistencePort()
        return FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            computePersonalRecords = computePersonalRecords,
            localDerivedStages = localDerivedTrainingStages(
                trainingLog = repo,
                extractTrainingInsights = insights.useCase,
                refreshWeeklySeries = RefreshWeeklySeriesUseCase(repo, FakeLiftRepository(), persistence),
            ),
            loggingPort = FakeLoggingPort(),
            refreshScope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private fun buildInsightsComponents(repo: FakeTrainingLogRepository): InsightsComponents {
        val liftInsightsRepo = RecordingLiftInsightsRepository()
        val metadataRepo = RecordingMetadataRepository()
        val useCase = ExtractTrainingInsightsUseCase(
            trainingLogRepository = repo,
            liftTrainingInsightsRepository = liftInsightsRepo,
            supersetAffinityRepository = NoopSupersetRepository(),
            muscleVolumeBaselineRepository = NoopVolumeRepository(),
            importAnalysisMetadataRepository = metadataRepo,
            liftRepository = FakeLiftRepository(),
        )
        return InsightsComponents(useCase, metadataRepo)
    }

    private data class InsightsComponents(
        val useCase: ExtractTrainingInsightsUseCase,
        val metadataRepo: RecordingMetadataRepository,
    )

    private class RecordingLiftInsightsRepository : LiftTrainingInsightsRepository {
        val stored = mutableListOf<LiftTrainingInsights>()
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) {
            stored.clear(); stored.addAll(insights)
        }
        override suspend fun getAll(): List<LiftTrainingInsights> = stored
        override suspend fun getByLiftId(liftId: com.stratum.domain.lift.identity.LiftId): LiftTrainingInsights? =
            stored.firstOrNull { it.liftId == liftId }
    }

    private class NoopLiftInsightsRepository : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit
        override suspend fun getAll(): List<LiftTrainingInsights> = emptyList()
        override suspend fun getByLiftId(liftId: com.stratum.domain.lift.identity.LiftId): LiftTrainingInsights? = null
    }

    private class NoopSupersetRepository : SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit
        override suspend fun getAll(): List<SupersetAffinity> = emptyList()
    }

    private class NoopVolumeRepository : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit
        override suspend fun getAll(): List<MuscleVolumeBaseline> = emptyList()
    }

    private class RecordingMetadataRepository : ImportAnalysisMetadataRepository {
        var saved: ImportAnalysisMetadata? = null
        override suspend fun save(metadata: ImportAnalysisMetadata) { saved = metadata }
        override suspend fun get(): ImportAnalysisMetadata? = saved
        override suspend fun clear() { saved = null }
    }

    private class ThrowingMetadataRepository : ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) = Unit
        override suspend fun get(): ImportAnalysisMetadata? = throw RuntimeException("simulated refresh failure")
        override suspend fun clear() = Unit
    }
}
