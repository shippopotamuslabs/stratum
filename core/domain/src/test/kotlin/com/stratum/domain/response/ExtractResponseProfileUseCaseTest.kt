package com.stratum.domain.response

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeExperimentBlockRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeResponseEpochRepository
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.importing.HistoricalSession
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.LoggedSet
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.TrainingHistoryRepository
import com.stratum.domain.response.fixtures.ResponseFixtures
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ExtractResponseProfileUseCaseTest {
    @Test
    @DisplayName("RP-S7-002 extract use case round trip")
    fun rpS7_002_extractUseCaseRoundTrip() = runTest {
        val fixture = ResponseFixtures.fixS7MultiEpoch()
        val historyRepo = FakeTrainingHistoryRepository(fixture.sessions, fixture.sets)
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(fixture.lifts)
        val epochRepo = FakeResponseEpochRepository()
        val volumeRepo = FakeVolumeResponseObservationRepository()
        val effectivenessRepo = FakeLiftEffectivenessObservationRepository()
        val experimentRepo = FakeExperimentBlockRepository()
        val metadataRepo = FakeResponseProfileMetadataRepository()
        val useCase =
            ExtractResponseProfileUseCase(
                trainingHistoryRepository = historyRepo,
                liftRepository = liftRepo,
                liftTrainingInsightsRepository = FakeLiftInsightsRepository(),
                muscleVolumeBaselineRepository = FakeVolumeBaselineRepository(),
                importAnalysisMetadataRepository = FakeImportMetadataRepository(fixture),
                responseEpochRepository = epochRepo,
                volumeResponseObservationRepository = volumeRepo,
                liftEffectivenessObservationRepository = effectivenessRepo,
                experimentBlockRepository = experimentRepo,
                responseProfileMetadataRepository = metadataRepo,
                extractor = DefaultResponseObservationExtractor(),
                experimentRegistry = EmptyExperimentRegistry(),
            )

        val result = useCase(fixture.provenLiftIds)

        assertTrue(result.isSuccess)
        assertEquals(2, epochRepo.getAll().size)
        assertEquals(2, metadataRepo.get()?.epochsAnalyzed)
    }

    @Test
    @DisplayName("RP-S7-003 empty experiment registry")
    fun rpS7_003_emptyExperimentRegistry() = runTest {
        val fixture = ResponseFixtures.fixS7MultiEpoch()
        val experimentRepo = FakeExperimentBlockRepository()
        val useCase = buildUseCase(fixture, experimentRepo)

        val summary = useCase(fixture.provenLiftIds).getOrThrow()

        assertEquals(0, experimentRepo.getAll().size)
        assertEquals(0, summary.experimentsRecorded)
    }

    @Test
    @DisplayName("RP-S7-004 summary fields")
    fun rpS7_004_summaryFields() = runTest {
        val fixture = ResponseFixtures.fixS7MultiEpoch()
        val useCase = buildUseCase(fixture, FakeExperimentBlockRepository())

        val summary = useCase(fixture.provenLiftIds).getOrThrow()

        assertEquals(2, summary.epochsSegmented)
        assertEquals(1, summary.musclesWithVolumeResponse)
        assertEquals(1, summary.patternsWithEffectiveness)
        assertEquals(0, summary.experimentsRecorded)
    }

    private fun buildUseCase(
        fixture: ResponseExtractionInput,
        experimentRepo: FakeExperimentBlockRepository,
    ): ExtractResponseProfileUseCase {
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(fixture.lifts)
        return ExtractResponseProfileUseCase(
            trainingHistoryRepository = FakeTrainingHistoryRepository(fixture.sessions, fixture.sets),
            liftRepository = liftRepo,
            liftTrainingInsightsRepository = FakeLiftInsightsRepository(),
            muscleVolumeBaselineRepository = FakeVolumeBaselineRepository(),
            importAnalysisMetadataRepository = FakeImportMetadataRepository(fixture),
            responseEpochRepository = FakeResponseEpochRepository(),
            volumeResponseObservationRepository = FakeVolumeResponseObservationRepository(),
            liftEffectivenessObservationRepository = FakeLiftEffectivenessObservationRepository(),
            experimentBlockRepository = experimentRepo,
            responseProfileMetadataRepository = FakeResponseProfileMetadataRepository(),
            extractor = DefaultResponseObservationExtractor(),
            experimentRegistry = EmptyExperimentRegistry(),
        )
    }

    private class FakeTrainingHistoryRepository(
        private var sessions: List<HistoricalSession>,
        private var sets: List<LoggedSet>,
    ) : TrainingHistoryRepository {
        override suspend fun replaceAll(sessions: List<HistoricalSession>, sets: List<LoggedSet>) {
            this.sessions = sessions
            this.sets = sets
        }

        override suspend fun getAllSessions(): List<HistoricalSession> = sessions

        override suspend fun getAllSets(): List<LoggedSet> = sets

        override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
            sets.filter { it.completedAt in windowStart..windowEnd }
    }

    private class FakeLiftInsightsRepository : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<com.stratum.domain.importing.LiftTrainingInsights>) = Unit

        override suspend fun getAll(): List<com.stratum.domain.importing.LiftTrainingInsights> = emptyList()

        override suspend fun getByLiftId(liftId: com.stratum.domain.lift.identity.LiftId) = null
    }

    private class FakeVolumeBaselineRepository : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<com.stratum.domain.importing.MuscleVolumeBaseline>) = Unit

        override suspend fun getAll(): List<com.stratum.domain.importing.MuscleVolumeBaseline> = emptyList()
    }

    private class FakeImportMetadataRepository(
        private val fixture: ResponseExtractionInput,
    ) : ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) = Unit

        override suspend fun get(): ImportAnalysisMetadata? =
            ImportAnalysisMetadata(
                windowStart = fixture.analysisWindowStart,
                windowEnd = fixture.analysisWindowEnd,
                provenLiftIds = fixture.provenLiftIds.toList(),
                unmatchedExerciseNames = emptyList(),
                totalSessions = fixture.sessions.size,
                totalSets = fixture.sets.size,
            )

        override suspend fun clear() = Unit
    }
}
