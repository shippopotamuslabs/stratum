package com.stratum.application.importing

import com.stratum.core.testing.importing.FakeCustomLiftInferencePort
import com.stratum.core.testing.importing.FakeImportEquipmentInferencePort
import com.stratum.core.testing.importing.FakeLiftEquipmentCatalog
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.export.ExportTestFixtures.ZONE_NY
import com.stratum.core.testing.export.ExportTestFixtures.session
import com.stratum.core.testing.export.ExportTestFixtures.sessionLive
import com.stratum.core.testing.export.ExportTestFixtures.set
import com.stratum.core.testing.export.ExportTestFixtures.setLive1
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.application.response.EmptyExperimentRegistry
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.training.TrainingSessionOrigin
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.application.response.ExtractResponseProfileUseCase
import com.stratum.application.training.ComputePersonalRecordsUseCase
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportDedupeSourceKind
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.SupersetAffinity

class PersistImportedHistoryMergeTest {

    private fun seedCatalog(): List<Lift> =
        List(50) { index ->
            Lift(
                id = LiftId("seed-filler-$index"),
                name = "Seed Filler $index",
                movementPatterns = setOf(MovementPattern.SquatPattern),
                primaryMuscles = setOf(MuscleGroup.Quad),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = false,
                isCustom = false,
            )
        } +
            Lift(
                id = LiftId("lift-squat"),
                name = "Squat",
                movementPatterns = setOf(MovementPattern.SquatPattern),
                primaryMuscles = setOf(MuscleGroup.Quad),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                isCustom = false,
            )

    private fun buildPersist(
        trainingLogRepo: FakeTrainingLogRepository,
        insightsRepo: LiftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
    ): PersistImportedHistoryUseCase {
        val liftRepo = FakeLiftRepository(initialLifts = seedCatalog())
        val metadataRepo = InMemoryMetadataRepository()
        val volumeRepo = InMemoryVolumeRepository()
        val persistence = FakeResponseProfilePersistencePort()
        val extractInsights =
            ExtractTrainingInsightsUseCase(
                trainingLogRepository = trainingLogRepo,
                liftTrainingInsightsRepository = insightsRepo,
                supersetAffinityRepository = InMemorySupersetRepository(),
                muscleVolumeBaselineRepository = volumeRepo,
                importAnalysisMetadataRepository = metadataRepo,
                liftRepository = liftRepo,
            )
        val extractResponse =
            ExtractResponseProfileUseCase(
                trainingLogRepository = trainingLogRepo,
                liftRepository = liftRepo,
                liftTrainingInsightsRepository = insightsRepo,
                muscleVolumeBaselineRepository = volumeRepo,
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
            extractTrainingInsights = extractInsights,
            extractResponseProfile = extractResponse,
            responseProfilePersistence = persistence,
            liftRepository = liftRepo,
            computePersonalRecords = ComputePersonalRecordsUseCase(trainingLogRepo),
        )
    }

    @Test
    @DisplayName("IE-S4-003 Merge append leaves LIVE sessions intact")
    fun ieS4_003_liveUntouched() = runTest {
        val repo =
            FakeTrainingLogRepository(
                initialSessions = listOf(sessionLive),
                initialSets = listOf(setLive1),
            )
        val incoming = session("imp-new", Instant.parse("2026-07-12T14:00:00Z"))
        val incomingSet = set("set-new", "imp-new", completedAt = Instant.parse("2026-07-12T14:05:00Z"))
        val result =
            buildPersist(repo)(
                sessions = listOf(incoming),
                sets = listOf(incomingSet),
                policy = ImportHistoryPersistPolicy.MergeSkipDuplicates,
                sourceKind = ImportDedupeSourceKind.ThirdParty,
                zone = ZONE_NY,
            )
        assertTrue(result.isSuccess)
        val all = repo.getAllSessions()
        assertTrue(all.any { it.id.value == "sess-live" && it.origin == TrainingSessionOrigin.LIVE })
        assertTrue(all.any { it.id.value == "imp-new" && it.origin == TrainingSessionOrigin.IMPORT })
        assertTrue(repo.getAllSets().any { it.id.value == "set-live1" })
    }

    @Test
    @DisplayName("IE-S4-005 Merge path failure does not clear prior import history")
    fun ieS4_005_mergeFailureNoWipe() = runTest {
        val existing1 = session("imp-1", Instant.parse("2026-07-01T14:00:00Z"))
        val existing2 = session("imp-2", Instant.parse("2026-07-02T14:00:00Z"))
        val set1 = set("s1", "imp-1")
        val set2 = set("s2", "imp-2")
        val repo =
            FakeTrainingLogRepository(
                initialSessions = listOf(existing1, existing2),
                initialSets = listOf(set1, set2),
            )
        assertEquals(2, repo.getImportSessions().size)
        val result =
            buildPersist(repo, insightsRepo = ThrowingLiftInsightsRepository("insights boom"))(
                sessions = listOf(session("imp-new", Instant.parse("2026-07-12T14:00:00Z"))),
                sets = listOf(set("set-new", "imp-new")),
                policy = ImportHistoryPersistPolicy.MergeSkipDuplicates,
                sourceKind = ImportDedupeSourceKind.ThirdParty,
                zone = ZONE_NY,
            )
        assertTrue(result.isFailure)
        assertEquals("insights boom", result.exceptionOrNull()!!.message)
        assertEquals(2, repo.getImportSessions().size)
    }

    @Test
    @DisplayName("IE-S4-006 All-duplicate Stratum merge succeeds with zero inserts")
    fun ieS4_006_allDuplicates() = runTest {
        val existing = session("sess-export-1", Instant.parse("2026-07-01T14:00:00Z"))
        val existingSet = set("set-export-1", "sess-export-1")
        val repo = FakeTrainingLogRepository(listOf(existing), listOf(existingSet))
        val result =
            buildPersist(repo)(
                sessions = listOf(existing),
                sets = listOf(existingSet),
                policy = ImportHistoryPersistPolicy.MergeSkipDuplicates,
                sourceKind = ImportDedupeSourceKind.StratumStableIds,
                zone = ZONE_NY,
            )
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow().mergeStats
        assertEquals(0, stats!!.sessionsInserted)
        assertEquals(0, stats.setsInserted)
        assertEquals(1, stats.sessionsSkipped)
    }

    @Test
    @DisplayName("IE-S4-007 ReplaceImportOrigin failure rolls back import history")
    fun ieS4_007_replaceRollback() = runTest {
        val repo = FakeTrainingLogRepository()
        val result =
            buildPersist(repo, insightsRepo = ThrowingLiftInsightsRepository("replace boom"))(
                sessions = listOf(session("imp-new", Instant.parse("2026-07-12T14:00:00Z"))),
                sets = listOf(set("set-new", "imp-new")),
                policy = ImportHistoryPersistPolicy.ReplaceImportOrigin,
            )
        assertTrue(result.isFailure)
        assertEquals(0, repo.getImportSessions().size)
    }

    @Test
    @DisplayName("IE-S4-M001 Mutation defense merge must not insert duplicate session id")
    fun ieS4_m001_noDuplicateSessionId() = runTest {
        val existing = session("dup-sess", Instant.parse("2026-07-01T14:00:00Z"))
        val existingSet = set("dup-set-old", "dup-sess")
        val repo = FakeTrainingLogRepository(listOf(existing), listOf(existingSet))
        val incomingSet = set("dup-set-new", "dup-sess")
        val result =
            buildPersist(repo)(
                sessions = listOf(existing),
                sets = listOf(incomingSet),
                policy = ImportHistoryPersistPolicy.MergeSkipDuplicates,
                sourceKind = ImportDedupeSourceKind.StratumStableIds,
                zone = ZONE_NY,
            )
        assertTrue(result.isSuccess)
        assertEquals(1, repo.getAllSessions().count { it.id.value == "dup-sess" })
        assertTrue(result.getOrThrow().mergeStats!!.sessionsSkipped >= 1)
    }

    @Test
    @DisplayName("IE-S4-009 Merge insights failure restores demoted LIVE PR flags")
    fun ieS4_009_mergeFailureRestoresDemotedPr() = runTest {
        val livePrE1rm = EstimatedOneRm.calculate(weight = 100.0, reps = 7) // 100x5@RIR2
        val livePr =
            set(
                id = "set-live-pr",
                sessionId = "sess-live",
                exercise = "Squat",
                weightAmount = 100.0,
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2026-07-10T18:05:00Z"),
            ).copy(
                liftId = LiftId("lift-squat"),
                isPersonalRecord = true,
                estimatedOneRm = livePrE1rm,
            )
        val repo =
            FakeTrainingLogRepository(
                initialSessions = listOf(sessionLive),
                initialSets = listOf(livePr),
            )
        // Heavier import set for the same lift → demotes LIVE PR before insights.
        val incoming =
            session("imp-new", Instant.parse("2026-07-12T14:00:00Z"))
        val incomingSet =
            set(
                id = "set-new",
                sessionId = "imp-new",
                exercise = "Squat",
                weightAmount = 200.0,
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2026-07-12T14:05:00Z"),
            )
        val result =
            buildPersist(repo, insightsRepo = ThrowingLiftInsightsRepository("insights boom"))(
                sessions = listOf(incoming),
                sets = listOf(incomingSet),
                policy = ImportHistoryPersistPolicy.MergeSkipDuplicates,
                sourceKind = ImportDedupeSourceKind.ThirdParty,
                zone = ZONE_NY,
            )
        assertTrue(result.isFailure)
        assertEquals("insights boom", result.exceptionOrNull()!!.message)
        // Appended import rows removed
        assertFalse(repo.getAllSessions().any { it.id.value == "imp-new" })
        assertFalse(repo.getAllSets().any { it.id.value == "set-new" })
        // LIVE PR flag restored after demotion unwind
        val restored = repo.getAllSets().single { it.id.value == "set-live-pr" }
        assertTrue(restored.isPersonalRecord, "demoted LIVE PR must be restored on merge unwind")
        assertEquals(LiftId("lift-squat"), restored.liftId)
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

    private class ThrowingLiftInsightsRepository(
        private val message: String,
    ) : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) {
            throw RuntimeException(message)
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
