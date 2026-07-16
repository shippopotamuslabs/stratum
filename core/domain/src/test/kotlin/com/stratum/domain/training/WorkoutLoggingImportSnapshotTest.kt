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
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import com.stratum.domain.importing.PersistImportedHistoryUseCase
import com.stratum.domain.importing.ResolveImportLiftsUseCase
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.domain.response.EmptyExperimentRegistry
import com.stratum.domain.response.ExtractResponseProfileUseCase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutLoggingImportSnapshotTest {
    @Test
    @DisplayName("WL-004 PersistImportedHistoryUseCase writes LoggedLiftContext on import sets")
    fun wl004_importPersistAttachesLiftSnapshot() = runTest {
        val trainingLogRepo = FakeTrainingLogRepository()
        val lifts = catalogWithBench()
        val liftRepo = FakeLiftRepository(initialLifts = lifts)
        val useCase = buildPersistUseCase(
            trainingLogRepo = trainingLogRepo,
            liftRepo = liftRepo,
        )

        useCase(
            sessions = listOf(WorkoutLoggingFixtures.importTrainingSession()),
            sets = listOf(WorkoutLoggingFixtures.importLoggedSet()),
            policy = ImportHistoryPersistPolicy.ReplaceImportOrigin,
        ).getOrThrow()

        val stored = trainingLogRepo.getImportSets().single()
        assertEquals("Barbell Bench Press", stored.liftContext.liftName)
        assertEquals(WorkoutLoggingFixtures.BENCH_HORIZONTAL_BITMASK, stored.liftContext.movementPatternsBitmask)
        assertEquals(WorkoutLoggingFixtures.CHEST_BITMASK, stored.liftContext.primaryMusclesBitmask)
    }

    @Test
    @DisplayName("WL-005 Import LoggedLiftContext has null template progression fields")
    fun wl005_importSnapshotNullTemplateFields() = runTest {
        val trainingLogRepo = FakeTrainingLogRepository()
        val lifts = catalogWithBench()
        val useCase = buildPersistUseCase(
            trainingLogRepo = trainingLogRepo,
            liftRepo = FakeLiftRepository(initialLifts = lifts),
        )

        useCase(
            sessions = listOf(WorkoutLoggingFixtures.importTrainingSession()),
            sets = listOf(WorkoutLoggingFixtures.importLoggedSet()),
            policy = ImportHistoryPersistPolicy.ReplaceImportOrigin,
        ).getOrThrow()

        val liftContext = trainingLogRepo.getImportSets().single().liftContext
        assertNull(liftContext.workoutLiftId)
        assertNull(liftContext.progressionScheme)
        assertNull(liftContext.setType)
        assertNull(liftContext.repMin)
        assertNull(liftContext.repMax)
        assertNull(liftContext.rirGoal)
    }

    private fun buildPersistUseCase(
        trainingLogRepo: FakeTrainingLogRepository,
        liftRepo: FakeLiftRepository,
    ): PersistImportedHistoryUseCase {
        val metadataRepo = InMemoryMetadataRepository()
        val insightsRepo = InMemoryLiftInsightsRepository()
        val persistence = FakeResponseProfilePersistencePort()
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
