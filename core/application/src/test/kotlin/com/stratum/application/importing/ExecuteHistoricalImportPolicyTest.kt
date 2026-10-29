package com.stratum.application.importing

import com.stratum.core.testing.importing.FakeHistoricalImportRunnerPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.export.ExportTestFixtures.session
import com.stratum.core.testing.export.ExportTestFixtures.set
import java.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.dashboard.windowStart
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.SupersetAffinity

class ExecuteHistoricalImportPolicyTest {

    private val emptyMetadata = object : ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) = Unit
        override suspend fun get(): ImportAnalysisMetadata? = null
        override suspend fun clear() = Unit
    }
    private val emptyInsights = object : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit
        override suspend fun getAll(): List<LiftTrainingInsights> = emptyList()
        override suspend fun getByLiftId(liftId: com.stratum.domain.lift.identity.LiftId) = null
    }
    private val emptySuperset = object : SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit
        override suspend fun getAll(): List<SupersetAffinity> = emptyList()
    }
    private val emptyVolume = object : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit
        override suspend fun getAll(): List<MuscleVolumeBaseline> = emptyList()
    }

    private fun buildRefresh(rollback: RollbackHistoricalImportUseCase): RefreshPersistedImportStateUseCase =
        RefreshPersistedImportStateUseCase(
            reconcileHistoricalImport = ReconcileHistoricalImportUseCase(emptyMetadata, rollback),
            loadHistoricalImportSummary =
                LoadHistoricalImportSummaryUseCase(
                    emptyMetadata,
                    emptyVolume,
                    emptyInsights,
                    emptySuperset,
                    FakeVolumeResponseObservationRepository(),
                    FakeLiftEffectivenessObservationRepository(),
                    FakeMuscleVolumeResponseSnapshotRepository(),
                    FakeLiftRepository(),
                ),
        )

    @Test
    @DisplayName("IE-S8-001 Onboarding historical import passes ReplaceImportOrigin")
    fun ieS8_001_explicitReplacePolicy() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val trainingLogRepo = FakeTrainingLogRepository()
        val rollback =
            RollbackHistoricalImportUseCase(
                trainingLogRepository = trainingLogRepo,
                liftTrainingInsightsRepository = emptyInsights,
                supersetAffinityRepository = emptySuperset,
                muscleVolumeBaselineRepository = emptyVolume,
                importAnalysisMetadataRepository = emptyMetadata,
                responseProfilePersistence = FakeResponseProfilePersistencePort(),
                liftRepository = FakeLiftRepository(),
            )
        val execute =
            ExecuteHistoricalImportUseCase(
                validateImportFiles = ValidateImportFilesUseCase(),
                runHistoricalImport = RunHistoricalImportUseCase(runner),
                refreshPersistedImportState = buildRefresh(rollback),
            )
        // Onboarding path must pass Replace explicitly (no default on the API).
        execute(
            files = listOf(ImportFileInput("x".toByteArray(), "export.csv")),
            resume = null,
            persistPolicy = ImportHistoryPersistPolicy.ReplaceImportOrigin,
        ).toList()
        assertEquals(
            ImportHistoryPersistPolicy.ReplaceImportOrigin,
            runner.invocations.last().persistPolicy,
        )
    }

    @Test
    @DisplayName("IE-S8-003 Execute does not double-rollback; runner owns Replace failure rollback")
    fun ieS8_003_executeDoesNotRollbackOnFailed() = runTest {
        val trainingLogRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session("imp-existing", Instant.parse("2026-07-01T14:00:00Z"))),
                initialSets = listOf(set("set-existing", "imp-existing")),
            )
        assertEquals(1, trainingLogRepo.getImportSessions().size)
        // Metadata present so refresh reconcile will not wipe history; isolates Execute.
        val metadataWithImport =
            object : ImportAnalysisMetadataRepository {
                override suspend fun save(metadata: ImportAnalysisMetadata) = Unit
                override suspend fun get(): ImportAnalysisMetadata? =
                    ImportAnalysisMetadata(
                        windowStart = Instant.parse("2026-07-01T00:00:00Z"),
                        windowEnd = Instant.parse("2026-07-12T00:00:00Z"),
                        provenLiftIds = emptyList(),
                        unmatchedExerciseNames = emptyList(),
                        totalSessions = 1,
                        totalSets = 1,
                    )
                override suspend fun clear() = Unit
            }
        val runner =
            FakeHistoricalImportRunnerPort(
                events = listOf(HistoricalImportEvent.Failed(message = "boom")),
            )
        val rollback =
            RollbackHistoricalImportUseCase(
                trainingLogRepository = trainingLogRepo,
                liftTrainingInsightsRepository = emptyInsights,
                supersetAffinityRepository = emptySuperset,
                muscleVolumeBaselineRepository = emptyVolume,
                importAnalysisMetadataRepository = metadataWithImport,
                responseProfilePersistence = FakeResponseProfilePersistencePort(),
                liftRepository = FakeLiftRepository(),
            )
        val execute =
            ExecuteHistoricalImportUseCase(
                validateImportFiles = ValidateImportFilesUseCase(),
                runHistoricalImport = RunHistoricalImportUseCase(runner),
                refreshPersistedImportState =
                    RefreshPersistedImportStateUseCase(
                        reconcileHistoricalImport =
                            ReconcileHistoricalImportUseCase(metadataWithImport, rollback),
                        loadHistoricalImportSummary =
                            LoadHistoricalImportSummaryUseCase(
                                metadataWithImport,
                                emptyVolume,
                                emptyInsights,
                                emptySuperset,
                                FakeVolumeResponseObservationRepository(),
                                FakeLiftEffectivenessObservationRepository(),
                                FakeMuscleVolumeResponseSnapshotRepository(),
                                FakeLiftRepository(),
                            ),
                    ),
            )
        val events =
            execute(
                files = listOf(ImportFileInput("x".toByteArray(), "export.csv")),
                resume = null,
                persistPolicy = ImportHistoryPersistPolicy.ReplaceImportOrigin,
            ).toList()
        assertTrue(events.any { it is HistoricalImportOrchestrationEvent.Failed })
        // Fake runner does not roll back; Execute must not wipe history either.
        assertEquals(1, trainingLogRepo.getImportSessions().size)
    }
}
