package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
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
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UpdateCompletedSessionSetsUseCaseTest {

    private val sessionId = TrainingSessionId("ts-completed-1")
    private val liftId = WorkoutLoggingFixtures.liftBenchId

    @Test
    @DisplayName("updates weight/reps/rir on a completed session set")
    fun updatesExistingSet() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(completedSession())
        val original = loggedSet("set-1", weight = 135.0, reps = 8, rir = 2)
        repo.seedSet(original)
        val scheduler = FakeResponseModelingSchedulerPort()
        val useCase = buildUseCase(repo, scheduler)

        val amended =
            original.copy(
                weight = Weight(145.0, Weight.WeightUnit.Lb),
                reps = 6,
                rir = 1,
            )
        val result = useCase(sessionId, listOf(amended))

        assertTrue(result.isSuccess)
        val stored = repo.getSetsForSession(sessionId).single()
        assertEquals(145.0, stored.weight.amount)
        assertEquals(6, stored.reps)
        assertEquals(1, stored.rir)
    }

    @Test
    @DisplayName("rejects edits when session is still in progress")
    fun rejectsInProgressSession() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = sessionId.value))
        val set = loggedSet("set-1")
        repo.seedSet(set)
        val useCase = buildUseCase(repo)

        val result = useCase(sessionId, listOf(set.copy(reps = 10)))

        assertTrue(result.isFailure)
        assertEquals(8, repo.getSetsForSession(sessionId).single().reps)
    }

    @Test
    @DisplayName("adds a new set and deletes a removed set on a completed session")
    fun addAndDeleteSets() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(completedSession())
        val keep = loggedSet("set-1", setIndex = 0)
        val remove = loggedSet("set-2", setIndex = 1, weight = 125.0)
        repo.seedSet(keep)
        repo.seedSet(remove)
        val useCase = buildUseCase(repo)

        val added =
            loggedSet("set-3", setIndex = 1, weight = 140.0, reps = 5, rir = 2)
        val result = useCase(sessionId, listOf(keep, added))

        assertTrue(result.isSuccess)
        val ids = repo.getSetsForSession(sessionId).map { it.id.value }.toSet()
        assertEquals(setOf("set-1", "set-3"), ids)
        assertEquals(2, repo.getSetsForSession(sessionId).size)
    }

    @Test
    @DisplayName("demotes PR on edited set and promotes next-best historical set")
    fun demotesAndPromotesPrAcrossHistory() = runTest {
        val repo = FakeTrainingLogRepository()
        val olderSessionId = TrainingSessionId("ts-older")
        repo.seedSession(
            completedSession(id = olderSessionId.value).copy(
                startedAt = Instant.parse("2024-12-01T10:00:00Z"),
                endedAt = Instant.parse("2024-12-01T11:00:00Z"),
            ),
        )
        repo.seedSession(completedSession())
        // Older session: strong set that should become PR after demotion
        val olderStrong =
            loggedSet(
                id = "older-strong",
                sessionId = olderSessionId,
                weight = 150.0,
                reps = 5,
                rir = 0,
                completedAt = Instant.parse("2024-12-01T10:30:00Z"),
            ).copy(isPersonalRecord = false, estimatedOneRm = null)
        // Current session: was PR, will be demoted
        val currentPr =
            loggedSet(
                id = "current-pr",
                weight = 200.0,
                reps = 5,
                rir = 0,
                completedAt = Instant.parse("2025-01-01T10:30:00Z"),
            ).copy(isPersonalRecord = true, estimatedOneRm = 200.0)
        repo.seedSet(olderStrong)
        repo.seedSet(currentPr)
        val useCase = buildUseCase(repo)

        val demoted =
            currentPr.copy(
                weight = Weight(100.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 0,
            )
        assertTrue(useCase(sessionId, listOf(demoted)).isSuccess)

        val older = repo.getSetsForLift(liftId).first { it.id.value == "older-strong" }
        val current = repo.getSetsForLift(liftId).first { it.id.value == "current-pr" }
        assertTrue(older.isPersonalRecord, "older stronger set should become PR")
        assertFalse(current.isPersonalRecord, "demoted set should lose PR")
    }

    @Test
    @DisplayName("forces dose-response invalidation when session was in last modeling run")
    fun forcesModelingWhenSessionInLastExtract() = runTest {
        val repo = FakeTrainingLogRepository()
        val session =
            completedSession().copy(
                startedAt = Instant.parse("2025-01-01T10:00:00Z"),
                endedAt = Instant.parse("2025-01-01T11:00:00Z"),
            )
        repo.seedSession(session)
        val set = loggedSet("set-1")
        repo.seedSet(set)
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(
            ResponseProfileMetadata(
                epochsAnalyzed = 1,
                analysisWindowStart = Instant.parse("2024-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-01-01T11:00:00Z"),
                // Watermark after the session — session was included in the extract
                lastExtractedAt = Instant.parse("2025-01-02T00:00:00Z"),
            ),
        )
        val scheduler = FakeResponseModelingSchedulerPort()
        val useCase = buildUseCase(repo, scheduler, metadataRepo)

        assertTrue(useCase(sessionId, listOf(set.copy(reps = 7))).isSuccess)
        assertEquals(1, scheduler.historyInvalidationCount)
        assertEquals(0, scheduler.enqueueCount)
    }

    @Test
    @DisplayName("does not force dose-response when session completed after last extract")
    fun skipsModelingWhenSessionAfterLastExtract() = runTest {
        val repo = FakeTrainingLogRepository()
        val session =
            completedSession().copy(
                startedAt = Instant.parse("2025-01-10T10:00:00Z"),
                endedAt = Instant.parse("2025-01-10T11:00:00Z"),
            )
        repo.seedSession(session)
        val set = loggedSet("set-1", completedAt = Instant.parse("2025-01-10T10:30:00Z"))
        repo.seedSet(set)
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(
            ResponseProfileMetadata(
                epochsAnalyzed = 1,
                analysisWindowStart = Instant.parse("2024-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-01-01T00:00:00Z"),
                // Last extract before this session — session not yet in a run
                lastExtractedAt = Instant.parse("2025-01-05T00:00:00Z"),
            ),
        )
        val scheduler = FakeResponseModelingSchedulerPort()
        val useCase = buildUseCase(repo, scheduler, metadataRepo)

        assertTrue(useCase(sessionId, listOf(set.copy(reps = 7))).isSuccess)
        assertEquals(0, scheduler.historyInvalidationCount)
        assertEquals(0, scheduler.enqueueCount)
    }

    @Test
    @DisplayName("does not enqueue modeling when no profile has been extracted yet")
    fun skipsModelingWhenNeverExtracted() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(completedSession())
        val set = loggedSet("set-1")
        repo.seedSet(set)
        val scheduler = FakeResponseModelingSchedulerPort()
        val useCase = buildUseCase(repo, scheduler, FakeResponseProfileMetadataRepository())

        assertTrue(useCase(sessionId, listOf(set.copy(reps = 7))).isSuccess)
        assertEquals(0, scheduler.historyInvalidationCount)
    }

    private fun completedSession(id: String = sessionId.value): TrainingSession =
        WorkoutLoggingFixtures.completedLiveSession(id = id)

    private fun loggedSet(
        id: String,
        sessionId: TrainingSessionId = this.sessionId,
        weight: Double = 135.0,
        reps: Int = 8,
        rir: Int? = 2,
        setIndex: Int = 0,
        completedAt: Instant = Instant.parse("2025-01-01T10:30:00Z"),
    ): LoggedSet =
        WorkoutLoggingFixtures.loggedSet(id = id, sessionId = sessionId).copy(
            weight = Weight(weight, Weight.WeightUnit.Lb),
            reps = reps,
            rir = rir,
            setIndex = setIndex,
            completedAt = completedAt,
            liftId = liftId,
        )

    private fun buildUseCase(
        repo: FakeTrainingLogRepository,
        scheduler: FakeResponseModelingSchedulerPort = FakeResponseModelingSchedulerPort(),
        metadataRepo: FakeResponseProfileMetadataRepository = FakeResponseProfileMetadataRepository(),
    ): UpdateCompletedSessionSetsUseCase {
        val insights = buildInsights(repo)
        val persistence = FakeResponseProfilePersistencePort(metadataRepository = metadataRepo)
        return UpdateCompletedSessionSetsUseCase(
            trainingLogRepository = repo,
            extractTrainingInsights = insights,
            refreshWeeklySeries = RefreshWeeklySeriesUseCase(repo, FakeLiftRepository(), persistence),
            metadataRepository = metadataRepo,
            responseModelingScheduler = scheduler,
            loggingPort = FakeLoggingPort(),
            refreshScope = CoroutineScope(Dispatchers.Unconfined),
            refreshDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun buildInsights(repo: FakeTrainingLogRepository): ExtractTrainingInsightsUseCase =
        ExtractTrainingInsightsUseCase(
            trainingLogRepository = repo,
            liftTrainingInsightsRepository = object : LiftTrainingInsightsRepository {
                override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit
                override suspend fun getAll(): List<LiftTrainingInsights> = emptyList()
                override suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsights? = null
            },
            supersetAffinityRepository = object : SupersetAffinityRepository {
                override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit
                override suspend fun getAll(): List<SupersetAffinity> = emptyList()
            },
            muscleVolumeBaselineRepository = object : MuscleVolumeBaselineRepository {
                override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit
                override suspend fun getAll(): List<MuscleVolumeBaseline> = emptyList()
            },
            importAnalysisMetadataRepository = object : ImportAnalysisMetadataRepository {
                override suspend fun save(metadata: ImportAnalysisMetadata) = Unit
                override suspend fun get(): ImportAnalysisMetadata? = null
                override suspend fun clear() = Unit
            },
            liftRepository = FakeLiftRepository(),
        )
}
