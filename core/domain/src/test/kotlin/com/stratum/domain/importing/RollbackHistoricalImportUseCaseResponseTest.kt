package com.stratum.domain.importing

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.ExperimentStatus
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.fixtures.ResponseFixtures
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.identity.ExperimentId
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.response.ExperimentBlock
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RollbackHistoricalImportUseCaseResponseTest {
    @Test
    @DisplayName("RP-S9-003 rollback clears response data")
    fun rpS9_003_rollbackClearsResponseData() = runTest {
        val persistence = FakeResponseProfilePersistencePort()
        seedResponseData(persistence)
        val rollback = buildRollback(
            trainingLogRepo = seededTrainingLogRepository(),
            importMetadataRepo = InMemoryMetadataRepository(),
            persistence = persistence,
        )

        rollback()

        assertEquals(0, persistence.epochRepository.getAll().size)
        assertEquals(0, persistence.volumeRepository.getAll().size)
        assertEquals(0, persistence.effectivenessRepository.getAll().size)
        assertEquals(0, persistence.experimentRepository.getAll().size)
        assertNull(persistence.metadataRepository.get())
    }

    @Test
    @DisplayName("RP-S9-004 reconcile without metadata")
    fun rpS9_004_reconcileWithoutMetadata() = runTest {
        val trainingLogRepo = seededTrainingLogRepository()
        val persistence = FakeResponseProfilePersistencePort()
        persistence.epochRepository.seed(
            listOf(
                ResponseFixtures.epoch(
                    id = "epoch-1",
                    start = Instant.parse("2025-01-01T00:00:00Z"),
                    end = Instant.parse("2025-03-01T00:00:00Z"),
                    splitReason = EpochSplitReason.WindowEnd,
                ),
            ),
        )
        val importMetadataRepo = InMemoryMetadataRepository()
        val rollback =
            buildRollback(
                trainingLogRepo = trainingLogRepo,
                importMetadataRepo = importMetadataRepo,
                persistence = persistence,
            )
        val reconcile = ReconcileHistoricalImportUseCase(importMetadataRepo, rollback)

        assertFalse(reconcile())
        assertEquals(0, trainingLogRepo.getImportSessions().size)
        assertEquals(0, persistence.epochRepository.getAll().size)
    }

    private fun buildRollback(
        trainingLogRepo: FakeTrainingLogRepository,
        importMetadataRepo: ImportAnalysisMetadataRepository,
        persistence: FakeResponseProfilePersistencePort,
    ): RollbackHistoricalImportUseCase =
        RollbackHistoricalImportUseCase(
            trainingLogRepository = trainingLogRepo,
            liftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
            supersetAffinityRepository = InMemorySupersetRepository(),
            muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
            importAnalysisMetadataRepository = importMetadataRepo,
            responseProfilePersistence = persistence,
            liftRepository = FakeLiftRepository(),
        )

    private suspend fun seededTrainingLogRepository(): FakeTrainingLogRepository {
        val repo = FakeTrainingLogRepository()
        repo.replaceImportHistory(
            sessions =
                listOf(
                    WorkoutLoggingFixtures.importTrainingSession(
                        id = "s1",
                        startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                        endedAt = null,
                        sourceApp = null,
                    ),
                ),
            sets =
                listOf(
                    WorkoutLoggingFixtures.importLoggedSet(
                        id = "set1",
                        sessionId = "s1",
                        exerciseNameRaw = "Squat",
                        liftId = null,
                        completedAt = Instant.parse("2025-01-01T01:00:00Z"),
                    ),
                ),
        )
        return repo
    }

    private suspend fun seedResponseData(
        persistence: FakeResponseProfilePersistencePort,
    ) {
        val epochId = EpochId("epoch-1")
        persistence.epochRepository.seed(
            listOf(
                ResponseFixtures.epoch(
                    id = epochId.value,
                    start = Instant.parse("2025-01-01T00:00:00Z"),
                    end = Instant.parse("2025-03-01T00:00:00Z"),
                ),
                ResponseFixtures.epoch(
                    id = "epoch-2",
                    start = Instant.parse("2025-03-16T00:00:00Z"),
                    end = Instant.parse("2025-06-01T00:00:00Z"),
                ),
            ),
        )
        persistence.volumeRepository.seed(
            listOf(
                VolumeResponseObservation(
                    muscle = MuscleGroup.Quad,
                    epochId = epochId,
                    anchorLiftId = ResponseFixtures.liftSquat,
                    stimulus =
                        StimulusDescriptor(
                            epochId = epochId,
                            experimentId = null,
                            muscle = MuscleGroup.Quad,
                            pattern = null,
                            liftId = ResponseFixtures.liftSquat,
                            weeklyHardSets = 10.0,
                            sessionsPerWeek = 2.0,
                            intensityProxy = 3.0,
                        ),
                    outcome =
                        OutcomeDescriptor(
                            anchorLiftId = ResponseFixtures.liftSquat,
                            metric = OutcomeMetric.EstimatedOneRmTrend,
                            value = 0.8,
                            windowWeeks = 10,
                            reliability = RegressionReliability(rSquared = 0.7, outlierCount = 0, completenessScore = 0.9),
                        ),
                    correlationSlope = 0.8,
                    confidenceScore = 0.8,
                    evidence = EvidenceLevel.Proven,
                    weeklyPointsUsed = 5,
                    epochWeeksUsed = 10,
                    observedWeeklySetRange = WeeklySetRange(min = 8.0, max = 12.0),
                    bestObservedWeeklySetRange = null,
                ),
            ),
        )
        persistence.effectivenessRepository.seed(emptyList())
        persistence.experimentRepository.replaceAll(
            listOf(
                ExperimentBlock(
                    id = ExperimentId("exp-1"),
                    prescribed =
                        StimulusDescriptor(
                            epochId = epochId,
                            experimentId = ExperimentId("exp-1"),
                            muscle = MuscleGroup.Quad,
                            pattern = MovementPattern.SquatPattern,
                            liftId = ResponseFixtures.liftSquat,
                            weeklyHardSets = 10.0,
                            sessionsPerWeek = 2.0,
                            intensityProxy = 3.0,
                        ),
                    outcomeWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                    outcomeWindowEnd = Instant.parse("2025-02-01T00:00:00Z"),
                    status = ExperimentStatus.Scheduled,
                ),
            ),
        )
        persistence.metadataRepository.save(
            ResponseProfileMetadata(
                epochsAnalyzed = 2,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                lastExtractedAt = Instant.parse("2026-01-15T00:00:00Z"),
            ),
        )
    }

    private class InMemoryMetadataRepository : ImportAnalysisMetadataRepository {
        private var stored: ImportAnalysisMetadata? = null

        override suspend fun save(metadata: ImportAnalysisMetadata) {
            stored = metadata
        }

        override suspend fun get(): ImportAnalysisMetadata? = stored

        override suspend fun clear() {
            stored = null
        }
    }

    private class InMemoryLiftInsightsRepository : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit

        override suspend fun getAll(): List<LiftTrainingInsights> = emptyList()

        override suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsights? = null
    }

    private class InMemorySupersetRepository : SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit

        override suspend fun getAll(): List<SupersetAffinity> = emptyList()
    }

    private class InMemoryVolumeRepository : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit

        override suspend fun getAll(): List<MuscleVolumeBaseline> = emptyList()
    }
}
