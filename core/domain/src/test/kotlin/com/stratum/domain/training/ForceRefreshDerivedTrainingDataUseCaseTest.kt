package com.stratum.domain.training

import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.localDerivedTrainingStages
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.ExtractedResponseProfile
import com.stratum.domain.response.ResponseProfilePersistencePort
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ForceRefreshDerivedTrainingDataUseCaseTest {
    private val persistence = FakeResponseProfilePersistencePort()
    private val modelingScheduler = FakeResponseModelingSchedulerPort()
    private val insightsRepo = FakeLiftTrainingInsightsRepository()
    private val metadataRepo = FakeImportAnalysisMetadataRepository()

    @Test
    @DisplayName("rebuilds insights + weekly series and force-enqueues with KEEP")
    fun rebuildsAndForceEnqueues() = runTest {
        val liftId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(liftId, MuscleGroup.Chest)))
        val weekStart = Instant.parse("2025-05-05T00:00:00Z")
        val logRepo = FakeTrainingLogRepository()
        // Insights require MIN_SESSIONS_FOR_FATIGUE (3) sessions per lift.
        repeat(3) { index ->
            val started = weekStart.plusSeconds(index * 86_400L)
            val session =
                WorkoutLoggingFixtures.completedLiveSession(id = "s$index").copy(startedAt = started)
            logRepo.seedSession(session)
            repeat(3) { setIndex ->
                logRepo.seedSet(
                    LoggedSet(
                        id = LoggedSetId("set-$index-$setIndex"),
                        sessionId = session.id,
                        exerciseNameRaw = "Bench",
                        liftId = liftId,
                        setIndex = setIndex,
                        weight = Weight(100.0 + setIndex, Weight.WeightUnit.Kg),
                        reps = 5,
                        rir = 2,
                        completedAt = started.plusSeconds(setIndex * 120L),
                        liftContext = LoggedLiftContext(liftId.value),
                    ),
                )
            }
        }

        val result = buildUseCase(logRepo, liftRepo).invoke()

        assertTrue(result.isSuccess)
        assertTrue(
            insightsRepo.getAll().isNotEmpty(),
            "force refresh must rebuild lift training insights",
        )
        assertNotNull(metadataRepo.get(), "force refresh must persist analysis metadata")
        assertTrue(
            persistence.weeklyStimulusRepository.getAll().isNotEmpty(),
            "force refresh must produce weekly stimulus for the dashboard",
        )
        assertTrue(
            persistence.liftPerformanceRepository.getAll().isNotEmpty(),
            "force refresh must produce lift weekly performance for the dashboard",
        )
        assertEquals(0, modelingScheduler.enqueueCount, "must not use gated enqueueRefresh")
        assertEquals(1, modelingScheduler.forceExtractCount)
        assertEquals(
            false,
            modelingScheduler.lastForceReplaceExisting,
            "recovery re-taps must KEEP in-flight force extracts",
        )
    }

    @Test
    @DisplayName("empty history still force-enqueues modeling after no-op stages")
    fun emptyHistoryStillForceEnqueues() = runTest {
        val result = buildUseCase(FakeTrainingLogRepository(), FakeLiftRepository()).invoke()

        assertTrue(result.isSuccess)
        assertTrue(persistence.weeklyStimulusRepository.getAll().isEmpty())
        assertEquals(0, modelingScheduler.enqueueCount)
        assertEquals(1, modelingScheduler.forceExtractCount)
        assertEquals(false, modelingScheduler.lastForceReplaceExisting)
    }

    @Test
    @DisplayName("insights failure does not enqueue modeling")
    fun insightsFailureSkipsEnqueue() = runTest {
        val failingLog =
            object : TrainingLogRepository by FakeTrainingLogRepository() {
                override suspend fun getAllSessions(): List<TrainingSession> {
                    error("simulated insights load failure")
                }

                override suspend fun getAllSets(): List<LoggedSet> = emptyList()
            }

        val result = buildUseCase(failingLog, FakeLiftRepository()).invoke()

        assertTrue(result.isFailure)
        assertEquals(0, modelingScheduler.enqueueCount)
        assertEquals(0, modelingScheduler.forceExtractCount)
    }

    @Test
    @DisplayName("weekly series failure does not enqueue modeling")
    fun weeklyFailureSkipsEnqueue() = runTest {
        val logRepo = FakeTrainingLogRepository()
        val liftRepo = FakeLiftRepository()
        val failingPersistence =
            object : ResponseProfilePersistencePort {
                override suspend fun replaceExtractedProfile(profile: ExtractedResponseProfile) = Unit
                override suspend fun replaceWeeklySeries(profile: ExtractedResponseProfile) {
                    error("simulated weekly persistence failure")
                }
                override suspend fun clearAll() = Unit
            }
        val weekStart = Instant.parse("2025-05-05T00:00:00Z")
        val liftId = LiftId("bench")
        liftRepo.seedLifts(listOf(lift(liftId, MuscleGroup.Chest)))
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").copy(startedAt = weekStart)
        logRepo.seedSession(session)
        logRepo.seedSet(
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = session.id,
                exerciseNameRaw = "Bench",
                liftId = liftId,
                setIndex = 0,
                weight = Weight(100.0, Weight.WeightUnit.Kg),
                reps = 5,
                rir = 2,
                completedAt = weekStart,
                liftContext = LoggedLiftContext(liftId.value),
            ),
        )
        val stages =
            LocalDerivedTrainingStages(
                extractTrainingInsights =
                    ExtractTrainingInsightsUseCase(
                        trainingLogRepository = logRepo,
                        liftTrainingInsightsRepository = insightsRepo,
                        supersetAffinityRepository = FakeSupersetAffinityRepository(),
                        muscleVolumeBaselineRepository = FakeMuscleVolumeBaselineRepository(),
                        importAnalysisMetadataRepository = metadataRepo,
                        liftRepository = liftRepo,
                    ),
                refreshWeeklySeries =
                    RefreshWeeklySeriesUseCase(logRepo, liftRepo, failingPersistence),
                loggingPort = FakeLoggingPort(),
                defaultDispatcher = Dispatchers.Unconfined,
            )

        val result =
            ForceRefreshDerivedTrainingDataUseCase(
                localDerivedStages = stages,
                responseModelingScheduler = modelingScheduler,
            ).invoke()

        assertTrue(result.isFailure)
        assertEquals(0, modelingScheduler.forceExtractCount)
    }

    private fun buildUseCase(
        logRepo: TrainingLogRepository,
        liftRepo: FakeLiftRepository,
    ): ForceRefreshDerivedTrainingDataUseCase =
        ForceRefreshDerivedTrainingDataUseCase(
            localDerivedStages =
                localDerivedTrainingStages(
                    trainingLog = logRepo,
                    liftRepository = liftRepo,
                    persistence = persistence,
                    extractTrainingInsights =
                        ExtractTrainingInsightsUseCase(
                            trainingLogRepository = logRepo,
                            liftTrainingInsightsRepository = insightsRepo,
                            supersetAffinityRepository = FakeSupersetAffinityRepository(),
                            muscleVolumeBaselineRepository = FakeMuscleVolumeBaselineRepository(),
                            importAnalysisMetadataRepository = metadataRepo,
                            liftRepository = liftRepo,
                        ),
                ),
            responseModelingScheduler = modelingScheduler,
        )

    private fun lift(id: LiftId, muscle: MuscleGroup): Lift =
        Lift(
            id = id,
            name = id.value,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(muscle),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )
}
