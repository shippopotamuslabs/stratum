package com.stratum.domain.importing

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeExperimentBlockRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeResponseEpochRepository
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.ExperimentStatus
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.fixtures.ResponseFixtures
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.identity.ExperimentId
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.response.ExperimentBlock
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RollbackHistoricalImportUseCaseResponseTest {
    @Test
    @DisplayName("RP-S9-003 rollback clears response data")
    fun rpS9_003_rollbackClearsResponseData() = runTest {
        val epochRepo = FakeResponseEpochRepository()
        val volumeRepo = FakeVolumeResponseObservationRepository()
        val effectivenessRepo = FakeLiftEffectivenessObservationRepository()
        val experimentRepo = FakeExperimentBlockRepository()
        val metadataRepo = FakeResponseProfileMetadataRepository()
        seedResponseData(epochRepo, volumeRepo, effectivenessRepo, experimentRepo, metadataRepo)
        val rollback = buildRollback(
            historyRepo = seededHistoryRepository(),
            importMetadataRepo = InMemoryMetadataRepository(),
            epochRepo = epochRepo,
            volumeRepo = volumeRepo,
            effectivenessRepo = effectivenessRepo,
            experimentRepo = experimentRepo,
            responseMetadataRepo = metadataRepo,
        )

        rollback()

        assertEquals(0, epochRepo.getAll().size)
        assertEquals(0, volumeRepo.getAll().size)
        assertEquals(0, effectivenessRepo.getAll().size)
        assertEquals(0, experimentRepo.getAll().size)
        assertNull(metadataRepo.get())
    }

    @Test
    @DisplayName("RP-S9-004 reconcile without metadata")
    fun rpS9_004_reconcileWithoutMetadata() = runTest {
        val historyRepo = seededHistoryRepository()
        val epochRepo = FakeResponseEpochRepository()
        epochRepo.seed(
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
                historyRepo = historyRepo,
                importMetadataRepo = importMetadataRepo,
                epochRepo = epochRepo,
                volumeRepo = FakeVolumeResponseObservationRepository(),
                effectivenessRepo = FakeLiftEffectivenessObservationRepository(),
                experimentRepo = FakeExperimentBlockRepository(),
                responseMetadataRepo = FakeResponseProfileMetadataRepository(),
            )
        val reconcile = ReconcileHistoricalImportUseCase(importMetadataRepo, rollback)

        assertFalse(reconcile())
        assertEquals(0, historyRepo.getAllSessions().size)
        assertEquals(0, epochRepo.getAll().size)
    }

    private fun buildRollback(
        historyRepo: InMemoryTrainingHistoryRepository,
        importMetadataRepo: ImportAnalysisMetadataRepository,
        epochRepo: FakeResponseEpochRepository,
        volumeRepo: FakeVolumeResponseObservationRepository,
        effectivenessRepo: FakeLiftEffectivenessObservationRepository,
        experimentRepo: FakeExperimentBlockRepository,
        responseMetadataRepo: ResponseProfileMetadataRepository,
    ): RollbackHistoricalImportUseCase =
        RollbackHistoricalImportUseCase(
            trainingHistoryRepository = historyRepo,
            liftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
            supersetAffinityRepository = InMemorySupersetRepository(),
            muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
            importAnalysisMetadataRepository = importMetadataRepo,
            responseEpochRepository = epochRepo,
            volumeResponseObservationRepository = volumeRepo,
            liftEffectivenessObservationRepository = effectivenessRepo,
            experimentBlockRepository = experimentRepo,
            responseProfileMetadataRepository = responseMetadataRepo,
            liftRepository = FakeLiftRepository(),
        )

    private fun seededHistoryRepository(): InMemoryTrainingHistoryRepository =
        InMemoryTrainingHistoryRepository(
            sessions =
                listOf(
                    HistoricalSession(
                        id = HistoricalSessionId("s1"),
                        startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                        endedAt = null,
                        sourceApp = null,
                    ),
                ),
            sets =
                listOf(
                    LoggedSet(
                        id = LoggedSetId("set1"),
                        sessionId = HistoricalSessionId("s1"),
                        exerciseNameRaw = "Squat",
                        liftId = null,
                        setIndex = 0,
                        weight = Weight(100.0, Weight.WeightUnit.Lb),
                        reps = 5,
                        rir = null,
                        completedAt = Instant.parse("2025-01-01T01:00:00Z"),
                    ),
                ),
        )

    private suspend fun seedResponseData(
        epochRepo: FakeResponseEpochRepository,
        volumeRepo: FakeVolumeResponseObservationRepository,
        effectivenessRepo: FakeLiftEffectivenessObservationRepository,
        experimentRepo: FakeExperimentBlockRepository,
        metadataRepo: FakeResponseProfileMetadataRepository,
    ) {
        val epochId = EpochId("epoch-1")
        epochRepo.seed(
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
        volumeRepo.seed(
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
        effectivenessRepo.seed(emptyList())
        experimentRepo.replaceAll(
            listOf(
                ExperimentBlock(
                    id = ExperimentId("exp-1"),
                    prescribed =
                        StimulusDescriptor(
                            epochId = epochId,
                            experimentId = ExperimentId("exp-1"),
                            muscle = MuscleGroup.Quad,
                            pattern = MovementPattern.LegPush,
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
        metadataRepo.save(
            ResponseProfileMetadata(
                epochsAnalyzed = 2,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                lastExtractedAt = Instant.parse("2026-01-15T00:00:00Z"),
            ),
        )
    }

    private class InMemoryTrainingHistoryRepository(
        private var sessions: List<HistoricalSession> = emptyList(),
        private var sets: List<LoggedSet> = emptyList(),
    ) : TrainingHistoryRepository {
        override suspend fun replaceAll(
            sessions: List<HistoricalSession>,
            sets: List<LoggedSet>,
        ) {
            this.sessions = sessions
            this.sets = sets
        }

        override suspend fun getAllSessions(): List<HistoricalSession> = sessions

        override suspend fun getAllSets(): List<LoggedSet> = sets

        override suspend fun getSetsInWindow(
            windowStart: Instant,
            windowEnd: Instant,
        ): List<LoggedSet> = emptyList()
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
