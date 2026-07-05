package com.stratum.domain.response

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.response.fixtures.ResponseFixtures
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
        val persistence = FakeResponseProfilePersistencePort()
        val useCase = buildUseCase(fixture, persistence)

        val result = useCase(fixture.provenLiftIds)

        assertTrue(result.isSuccess)
        assertEquals(2, persistence.epochRepository.getAll().size)
        assertEquals(2, persistence.metadataRepository.get()?.epochsAnalyzed)
    }

    @Test
    @DisplayName("RP-S7-003 empty experiment registry")
    fun rpS7_003_emptyExperimentRegistry() = runTest {
        val fixture = ResponseFixtures.fixS7MultiEpoch()
        val persistence = FakeResponseProfilePersistencePort()
        val useCase = buildUseCase(fixture, persistence)

        val summary = useCase(fixture.provenLiftIds).getOrThrow()

        assertEquals(0, persistence.experimentRepository.getAll().size)
        assertEquals(0, summary.experimentsRecorded)
    }

    @Test
    @DisplayName("RP-S7-004 summary fields")
    fun rpS7_004_summaryFields() = runTest {
        val fixture = ResponseFixtures.fixS7MultiEpoch()
        val useCase = buildUseCase(fixture, FakeResponseProfilePersistencePort())

        val summary = useCase(fixture.provenLiftIds).getOrThrow()

        assertEquals(2, summary.epochsSegmented)
        assertEquals(1, summary.musclesWithVolumeResponse)
        assertEquals(1, summary.patternsWithEffectiveness)
        assertEquals(0, summary.experimentsRecorded)
    }

    @Test
    @DisplayName("RP-S7-005 empty history does not crash")
    fun rpS7_005_emptyHistoryDoesNotCrash() = runTest {
        val persistence = FakeResponseProfilePersistencePort()
        val useCase =
            ExtractResponseProfileUseCase(
                trainingLogRepository = FakeTrainingLogRepository(),
                liftRepository = FakeLiftRepository(),
                liftTrainingInsightsRepository = FakeLiftInsightsRepository(),
                muscleVolumeBaselineRepository = FakeVolumeBaselineRepository(),
                importAnalysisMetadataRepository = FakeImportMetadataRepository(null),
                responseProfilePersistence = persistence,
                extractor = DefaultResponseObservationExtractor(),
                experimentRegistry = EmptyExperimentRegistry(),
            )

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, persistence.epochRepository.getAll().size)
    }

    @Test
    @DisplayName("WL-042 ExtractResponseProfileUseCase computes volume baselines without live Lift list")
    fun wl042_volumeBaselinesWithoutCatalog() = runTest {
        val sets = listOf(
            com.stratum.core.testing.training.WorkoutLoggingFixtures.loggedSet(
                id = "set-1",
                sessionId = com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
            ),
        )

        val baselines = ExtractResponseProfileUseCase.extractVolumeBaselinesFromSnapshots(sets)

        assertTrue(baselines.isNotEmpty())
        assertTrue((baselines.firstOrNull { it.muscle == com.stratum.domain.onboarding.MuscleGroup.Chest }?.meanWeeklyHardSets ?: 0.0) > 0.0)
    }

    private suspend fun buildUseCase(
        fixture: ResponseExtractionInput,
        persistence: FakeResponseProfilePersistencePort,
    ): ExtractResponseProfileUseCase {
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(fixture.lifts)
        val trainingLogRepo = FakeTrainingLogRepository()
        trainingLogRepo.replaceImportHistory(fixture.sessions, fixture.sets)
        return ExtractResponseProfileUseCase(
            trainingLogRepository = trainingLogRepo,
            liftRepository = liftRepo,
            liftTrainingInsightsRepository = FakeLiftInsightsRepository(),
            muscleVolumeBaselineRepository = FakeVolumeBaselineRepository(),
            importAnalysisMetadataRepository = FakeImportMetadataRepository(fixture),
            responseProfilePersistence = persistence,
            extractor = DefaultResponseObservationExtractor(),
            experimentRegistry = EmptyExperimentRegistry(),
        )
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
        private val fixture: ResponseExtractionInput?,
    ) : ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) = Unit

        override suspend fun get(): ImportAnalysisMetadata? =
            fixture?.let {
                ImportAnalysisMetadata(
                    windowStart = it.analysisWindowStart,
                    windowEnd = it.analysisWindowEnd,
                    provenLiftIds = it.provenLiftIds.toList(),
                    unmatchedExerciseNames = emptyList(),
                    totalSessions = it.sessions.size,
                    totalSets = it.sets.size,
                )
            }

        override suspend fun clear() = Unit
    }
}
