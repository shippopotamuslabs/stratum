package com.stratum.domain.importing

import com.stratum.core.testing.importing.FakeCustomLiftInferencePort
import com.stratum.core.testing.importing.FakeImportEquipmentInferencePort
import com.stratum.core.testing.importing.FakeLiftEquipmentCatalog
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.response.FailOnVolumeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.domain.response.EmptyExperimentRegistry
import org.junit.jupiter.api.Assertions.assertNull
import com.stratum.domain.response.ExtractResponseProfileUseCase
import com.stratum.domain.response.ResponseProfilePersistencePort
import com.stratum.domain.response.ResponseExtractionInput
import com.stratum.domain.response.fixtures.ResponseFixtures
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PersistImportedHistoryUseCaseResponseTest {
    @Test
    @DisplayName("RP-S9-001 import orchestration order")
    fun rpS9_001_importOrchestrationOrder() = runTest {
        val fixture = ResponseFixtures.fixS9ImportOk()
        val trainingLogRepo = FakeTrainingLogRepository()
        val metadataRepo = InMemoryMetadataRepository()
        val insightsRepo = InMemoryLiftInsightsRepository()
        val liftRepo = FakeLiftRepository(initialLifts = catalogWithFixtureLifts(fixture))
        val persistence = FakeResponseProfilePersistencePort()
        val useCase = buildPersistUseCase(
            fixture = fixture,
            trainingLogRepo = trainingLogRepo,
            metadataRepo = metadataRepo,
            insightsRepo = insightsRepo,
            liftRepo = liftRepo,
            persistence = persistence,
        )

        val result = useCase(fixture.sessions, fixture.sets)

        assertTrue(result.isSuccess)
        assertNotNull(metadataRepo.get())
        assertTrue(insightsRepo.getAll().isNotEmpty())
        assertEquals(2, persistence.epochRepository.getAll().size)
        assertNotNull(persistence.metadataRepository.get())
    }

    @Test
    @DisplayName("RP-S9-002 response failure rollback")
    fun rpS9_002_responseFailureRollback() = runTest {
        val fixture = ResponseFixtures.fixS9ImportOk()
        val trainingLogRepo = FakeTrainingLogRepository()
        val persistence = FailOnVolumeResponseProfilePersistencePort()
        val useCase = buildPersistUseCase(
            fixture = fixture,
            trainingLogRepo = trainingLogRepo,
            metadataRepo = InMemoryMetadataRepository(),
            insightsRepo = InMemoryLiftInsightsRepository(),
            liftRepo = FakeLiftRepository(initialLifts = catalogWithFixtureLifts(fixture)),
            persistence = persistence,
        )

        val result = useCase(fixture.sessions, fixture.sets)

        assertTrue(result.isFailure)
        assertEquals(0, trainingLogRepo.getImportSessions().size)
        assertEquals(0, persistence.epochRepository.getAll().size)
        assertEquals(0, persistence.volumeRepository.getAll().size)
    }

    @Test
    @DisplayName("RP-S9-004 response failure on second repo rolls back persisted epochs")
    fun rpS9_004_responseFailureOnSecondRepoRollsBackEpochs() = runTest {
        val fixture = ResponseFixtures.fixS9ImportOk()
        val trainingLogRepo = FakeTrainingLogRepository()
        val persistence = FailOnVolumeResponseProfilePersistencePort()
        val useCase = buildPersistUseCase(
            fixture = fixture,
            trainingLogRepo = trainingLogRepo,
            metadataRepo = InMemoryMetadataRepository(),
            insightsRepo = InMemoryLiftInsightsRepository(),
            liftRepo = FakeLiftRepository(initialLifts = catalogWithFixtureLifts(fixture)),
            persistence = persistence,
        )

        val result = useCase(fixture.sessions, fixture.sets)

        assertTrue(result.isFailure)
        assertEquals(0, persistence.epochRepository.getAll().size)
        assertEquals(0, persistence.volumeRepository.getAll().size)
        assertNull(persistence.metadataRepository.get())
    }

    @Test
    @DisplayName("RP-S9-005 insight failure still rolls back")
    fun rpS9_005_insightFailureStillRollsBack() = runTest {
        val fixture = ResponseFixtures.fixS9ImportOk()
        val trainingLogRepo = FakeTrainingLogRepository()
        val persistence = FakeResponseProfilePersistencePort()
        val useCase = buildPersistUseCase(
            fixture = fixture,
            trainingLogRepo = trainingLogRepo,
            metadataRepo = InMemoryMetadataRepository(),
            insightsRepo = ThrowingLiftInsightsRepository(),
            liftRepo = FakeLiftRepository(initialLifts = catalogWithFixtureLifts(fixture)),
            persistence = persistence,
        )

        val result = useCase(fixture.sessions, fixture.sets)

        assertTrue(result.isFailure)
        assertEquals(0, trainingLogRepo.getImportSessions().size)
        assertEquals(0, persistence.epochRepository.getAll().size)
    }

    private fun buildPersistUseCase(
        fixture: ResponseExtractionInput,
        trainingLogRepo: FakeTrainingLogRepository,
        metadataRepo: InMemoryMetadataRepository,
        insightsRepo: LiftTrainingInsightsRepository,
        liftRepo: FakeLiftRepository,
        persistence: ResponseProfilePersistencePort,
    ): PersistImportedHistoryUseCase {
        val extractResponseProfile =
            ExtractResponseProfileUseCase(
                trainingLogRepository = trainingLogRepo,
                liftRepository = liftRepo,
                liftTrainingInsightsRepository = insightsRepo,
                muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
                importAnalysisMetadataRepository = metadataRepo,
                responseProfilePersistence = persistence,
                extractor = DefaultResponseObservationExtractor(),
                experimentRegistry = EmptyExperimentRegistry(),
            )
        return PersistImportedHistoryUseCase(
            trainingLogRepository = trainingLogRepo,
            resolveImportLifts =
                ResolveImportLiftsUseCase(
                    liftRepo,
                    Bm25LiftNameResolver(),
                    EnrichCustomLiftsUseCase(FakeCustomLiftInferencePort()),
                    FakeLiftEquipmentCatalog(),
                    FakeImportEquipmentInferencePort(),
                ),
            extractTrainingInsights =
                ExtractTrainingInsightsUseCase(
                    trainingLogRepository = trainingLogRepo,
                    liftTrainingInsightsRepository = insightsRepo,
                    supersetAffinityRepository = InMemorySupersetRepository(),
                    muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
                    importAnalysisMetadataRepository = metadataRepo,
                    liftRepository = liftRepo,
                ),
            extractResponseProfile = extractResponseProfile,
            responseProfilePersistence = persistence,
            liftRepository = liftRepo,
        )
    }

    private fun catalogWithFixtureLifts(fixture: ResponseExtractionInput): List<Lift> {
        val fillers =
            List(50) { index ->
                Lift(
                    id = LiftId("seed-filler-$index"),
                    name = "Seed Filler $index",
                    movementPatterns = setOf(MovementPattern.SquatPattern),
                    primaryMuscles = emptySet(),
                    secondaryMuscles = emptySet(),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = false,
                    isCustom = false,
                )
            }
        return fillers + fixture.lifts
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
        private val insights = mutableListOf<LiftTrainingInsights>()

        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) {
            this.insights.clear()
            this.insights.addAll(insights)
        }

        override suspend fun getAll(): List<LiftTrainingInsights> = insights.toList()

        override suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsights? =
            insights.firstOrNull { it.liftId == liftId }
    }

    private class ThrowingLiftInsightsRepository : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) {
            throw IllegalStateException("insight extraction failed")
        }

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
