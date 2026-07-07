package com.stratum.domain.training

import com.stratum.core.testing.importing.FakeCustomLiftInferencePort
import com.stratum.core.testing.importing.FakeImportEquipmentInferencePort
import com.stratum.core.testing.importing.FakeLiftEquipmentCatalog
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.Bm25LiftNameResolver
import com.stratum.domain.importing.EnrichCustomLiftsUseCase
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.PersistImportedHistoryUseCase
import com.stratum.domain.importing.ResolveImportLiftsUseCase
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.importing.TrainingInsightsExtractor
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.domain.response.EmptyExperimentRegistry
import com.stratum.domain.response.ExtractResponseProfileUseCase
import com.stratum.domain.response.ResponseProfilePersistencePort
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutLoggingMutationDefenseTest {
    @Test
    @DisplayName("WL-007 Renaming lift in catalog after import does not change credited volume")
    fun wl007_catalogMutationDoesNotChangeImportVolume() = runTest {
        val trainingLogRepo = FakeTrainingLogRepository()
        val lifts = catalogWithBench()
        val liftRepo = FakeLiftRepository(initialLifts = lifts)
        val persistUseCase = buildPersistUseCase(
            trainingLogRepo = trainingLogRepo,
            liftRepo = liftRepo,
        )

        persistUseCase(
            sessions = listOf(WorkoutLoggingFixtures.importTrainingSession()),
            sets = listOf(WorkoutLoggingFixtures.importLoggedSet()),
        ).getOrThrow()

        liftRepo.seedLifts(catalogWithBench().map { lift ->
            if (lift.id == WorkoutLoggingFixtures.liftBenchId) WorkoutLoggingFixtures.backOnlyBench() else lift
        })

        val baselinesFromSnapshot =
            TrainingInsightsExtractor.computeVolumeBaselinesFromSnapshots(trainingLogRepo.getImportSets())

        val chest = baselinesFromSnapshot.firstOrNull { it.muscle == MuscleGroup.Chest }
        val back = baselinesFromSnapshot.firstOrNull { it.muscle == MuscleGroup.Back }

        assertEquals(1.0, chest?.meanWeeklyHardSets ?: 0.0, 0.001)
        assertEquals(0.0, back?.meanWeeklyHardSets ?: 0.0, 0.001)
    }

    @Test
    @DisplayName("WL-044 ExtractResponseProfile volume path ignores mutated catalog when snapshots present")
    fun wl044_analyticsIgnoreMutatedCatalog() = runTest {
        val trainingLogRepo = FakeTrainingLogRepository()
        val lifts = catalogWithBench()
        val liftRepo = FakeLiftRepository(initialLifts = lifts)
        val persistence = FakeResponseProfilePersistencePort()
        val persistUseCase = buildPersistUseCase(
            trainingLogRepo = trainingLogRepo,
            liftRepo = liftRepo,
            persistence = persistence,
        )

        persistUseCase(
            sessions = listOf(WorkoutLoggingFixtures.importTrainingSession()),
            sets = listOf(WorkoutLoggingFixtures.importLoggedSet()),
        ).getOrThrow()

        liftRepo.seedLifts(listOf(WorkoutLoggingFixtures.verticalPullCatalogBench()))

        val extractUseCase =
            ExtractResponseProfileUseCase(
                trainingLogRepository = trainingLogRepo,
                liftRepository = liftRepo,
                liftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
                muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
                importAnalysisMetadataRepository = InMemoryMetadataRepository(),
                responseProfilePersistence = persistence,
                extractor = DefaultResponseObservationExtractor(),
                experimentRegistry = EmptyExperimentRegistry(),
            )

        extractUseCase().getOrThrow()

        val horizontalPushCredit =
            persistence.effectivenessRepository
                .getAll()
                .filter { it.pattern == MovementPattern.HorizontalPress }
                .maxOfOrNull { it.effectivenessScore }
                ?: 0.0
        val verticalPullCredit =
            persistence.effectivenessRepository
                .getAll()
                .filter { it.pattern == MovementPattern.VerticalPull }
                .maxOfOrNull { it.effectivenessScore }
                ?: 0.0

        assertTrue(horizontalPushCredit > 0.0)
        assertEquals(0.0, verticalPullCredit, 0.001)
    }

    private fun buildPersistUseCase(
        trainingLogRepo: FakeTrainingLogRepository,
        liftRepo: FakeLiftRepository,
        persistence: ResponseProfilePersistencePort = FakeResponseProfilePersistencePort(),
    ): PersistImportedHistoryUseCase {
        val metadataRepo = InMemoryMetadataRepository()
        val insightsRepo = InMemoryLiftInsightsRepository()
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
            computePersonalRecords = com.stratum.domain.training.ComputePersonalRecordsUseCase(trainingLogRepo),
        )
    }

    private fun catalogWithBench(): List<Lift> {
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
        return fillers + WorkoutLoggingFixtures.benchLift()
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

    private class InMemorySupersetRepository : SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit

        override suspend fun getAll(): List<SupersetAffinity> = emptyList()
    }

    private class InMemoryVolumeRepository : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit

        override suspend fun getAll(): List<MuscleVolumeBaseline> = emptyList()
    }
}
