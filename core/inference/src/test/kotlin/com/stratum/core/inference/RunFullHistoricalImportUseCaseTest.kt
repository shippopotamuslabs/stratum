package com.stratum.core.inference

import com.stratum.core.testing.importing.FakeHistoricalImportPort
import com.stratum.core.testing.importing.FakeImportJobRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.importing.Bm25LiftNameResolver
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.MergeImportedHistoryUseCase
import com.stratum.domain.importing.HistoricalSession
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.ImportJobSnapshot
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.LoggedSet
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeCustomLiftInferencePort
import com.stratum.core.testing.importing.FakeImportEquipmentInferencePort
import com.stratum.core.testing.importing.FakeLiftEquipmentCatalog
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.importing.EnrichCustomLiftsUseCase
import com.stratum.domain.importing.PersistImportedHistoryUseCase
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.domain.response.EmptyExperimentRegistry
import com.stratum.domain.response.ExtractResponseProfileUseCase
import com.stratum.domain.importing.RollbackHistoricalImportUseCase
import com.stratum.domain.importing.RemoteImportJobStatus
import com.stratum.domain.importing.ResolveImportLiftsUseCase
import com.stratum.domain.importing.StartHistoricalImportUseCase
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.importing.TrainingHistoryRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import java.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RunFullHistoricalImportUseCaseTest {
    @Test
    fun `emits progress then completes when server returns ready snapshot`() = runTest {
        val jobRepo = FakeImportJobRepository()
        val historyRepo = InMemoryTrainingHistoryRepository()
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val port = FakeHistoricalImportPort(
            snapshotSequence = listOf(
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.AgentRunning,
                    progressPhase = ImportProgressPhase.ConvertingFull,
                    progressMessage = "Converting",
                    conversionStoragePath = null,
                    errorMessage = null,
                ),
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.Ready,
                    progressPhase = ImportProgressPhase.ConvertingFull,
                    progressMessage = "Done",
                    conversionStoragePath = "imports/test/job/conversions/1/normalized.json",
                    errorMessage = null,
                ),
            ),
            conversionJson = sampleConversionJson(),
        )
        val (persist, rollback) = buildPersistAndRollback(historyRepo, liftRepo)
        val useCase = RunFullHistoricalImportUseCase(
            startHistoricalImport = StartHistoricalImportUseCase(jobRepo),
            historicalImportPort = port,
            persistImportedHistory = persist,
            rollbackHistoricalImport = rollback,
            importJobRepository = jobRepo,
            mergeImportedHistory = MergeImportedHistoryUseCase(),
            importLiftResolutionLogger = testImportLiftResolutionLogger(),
            loggingPort = FakeLoggingPort(),
        )

        val events =
            useCase.run(
                listOf(ImportFileInput("id,exercise_name\n1,Squat".toByteArray(), "export.csv")),
            ).toList()

        assertTrue(events.filterIsInstance<HistoricalImportEvent.Progress>().isNotEmpty())
        assertTrue(events.last() is HistoricalImportEvent.Completed)
        assertTrue(port.uploads.isNotEmpty())
        assertEquals(1, jobRepo.allJobs().size)
        assertEquals(ImportJobStatus.Completed, jobRepo.allJobs().first().status)
    }

    @Test
    fun `merges multiple files before persisting once`() = runTest {
        val jobRepo = FakeImportJobRepository()
        val historyRepo = InMemoryTrainingHistoryRepository()
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val readySnapshot1 =
            listOf(
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.Ready,
                    progressPhase = ImportProgressPhase.ConvertingFull,
                    progressMessage = "Done",
                    conversionStoragePath = "imports/test/job/conversions/1/normalized.json",
                    errorMessage = null,
                ),
            )
        val readySnapshot2 =
            listOf(
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.Ready,
                    progressPhase = ImportProgressPhase.ConvertingFull,
                    progressMessage = "Done",
                    conversionStoragePath = "imports/test/job/conversions/2/normalized.json",
                    errorMessage = null,
                ),
            )
        val port =
            FakeHistoricalImportPort(
                snapshotSequenceByUpload = listOf(readySnapshot1, readySnapshot2),
                conversionJsonMap = mapOf(
                    "imports/test/job/conversions/1/normalized.json" to sampleConversionJson(id = "set1", time = "2025-01-01T01:00:00Z"),
                    "imports/test/job/conversions/2/normalized.json" to sampleConversionJson(id = "set2", time = "2025-01-01T01:05:00Z")
                ),
            )
        val (persist, rollback) = buildPersistAndRollback(historyRepo, liftRepo)
        val useCase =
            RunFullHistoricalImportUseCase(
                startHistoricalImport = StartHistoricalImportUseCase(jobRepo),
                historicalImportPort = port,
                persistImportedHistory = persist,
                rollbackHistoricalImport = rollback,
                importJobRepository = jobRepo,
                mergeImportedHistory = MergeImportedHistoryUseCase(),
                importLiftResolutionLogger = testImportLiftResolutionLogger(),
                loggingPort = FakeLoggingPort(),
            )

        val events =
            useCase.run(
                listOf(
                    ImportFileInput("a".toByteArray(), "a.csv"),
                    ImportFileInput("b".toByteArray(), "b.csv"),
                ),
            ).toList()

        assertTrue(events.last() is HistoricalImportEvent.Completed)
        assertEquals(2, port.uploads.size)
        assertEquals(2, jobRepo.allJobs().count { it.status == ImportJobStatus.Completed })
        assertEquals(2, historyRepo.getAllSessions().size)
        assertEquals(2, historyRepo.getAllSets().size)
    }

    @Test
    fun `rolls back when second file conversion fails`() = runTest {
        val jobRepo = FakeImportJobRepository()
        val historyRepo = InMemoryTrainingHistoryRepository()
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val readySnapshot =
            listOf(
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.Ready,
                    progressPhase = ImportProgressPhase.ConvertingFull,
                    progressMessage = "Done",
                    conversionStoragePath = "imports/test/job/conversions/1/normalized.json",
                    errorMessage = null,
                ),
            )
        val failedSnapshot =
            listOf(
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.Failed,
                    progressPhase = ImportProgressPhase.Failed,
                    progressMessage = null,
                    conversionStoragePath = null,
                    errorMessage = "Conversion failed.",
                ),
            )
        val port =
            FakeHistoricalImportPort(
                snapshotSequenceByUpload = listOf(readySnapshot, failedSnapshot),
                conversionJson = sampleConversionJson(),
            )
        val (persist, rollback) = buildPersistAndRollback(historyRepo, liftRepo)
        val useCase =
            RunFullHistoricalImportUseCase(
                startHistoricalImport = StartHistoricalImportUseCase(jobRepo),
                historicalImportPort = port,
                persistImportedHistory = persist,
                rollbackHistoricalImport = rollback,
                importJobRepository = jobRepo,
                mergeImportedHistory = MergeImportedHistoryUseCase(),
                importLiftResolutionLogger = testImportLiftResolutionLogger(),
                loggingPort = FakeLoggingPort(),
            )

        val events =
            useCase.run(
                listOf(
                    ImportFileInput("a".toByteArray(), "a.csv"),
                    ImportFileInput("b".toByteArray(), "b.csv"),
                ),
            ).toList()

        assertTrue(events.last() is HistoricalImportEvent.Failed)
        val failed = events.last() as HistoricalImportEvent.Failed
        assertNotNull(failed.resume)
        assertEquals(1, failed.resume?.completedChunks?.size)
        assertTrue(historyRepo.getAllSets().isEmpty())
    }

    @Test
    fun `resume retries only pending files after partial multi-file failure`() = runTest {
        val jobRepo = FakeImportJobRepository()
        val historyRepo = InMemoryTrainingHistoryRepository()
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val readySnapshot1 =
            listOf(
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.Ready,
                    progressPhase = ImportProgressPhase.ConvertingFull,
                    progressMessage = "Done",
                    conversionStoragePath = "imports/test/job/conversions/1/normalized.json",
                    errorMessage = null,
                ),
            )
        val readySnapshot2 =
            listOf(
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.Ready,
                    progressPhase = ImportProgressPhase.ConvertingFull,
                    progressMessage = "Done",
                    conversionStoragePath = "imports/test/job/conversions/2/normalized.json",
                    errorMessage = null,
                ),
            )
        val failedSnapshot =
            listOf(
                ImportJobSnapshot(
                    status = RemoteImportJobStatus.Failed,
                    progressPhase = ImportProgressPhase.Failed,
                    progressMessage = null,
                    conversionStoragePath = null,
                    errorMessage = "Conversion failed.",
                ),
            )
        val port =
            FakeHistoricalImportPort(
                snapshotSequenceByUpload = listOf(readySnapshot1, failedSnapshot, readySnapshot2),
                conversionJsonMap = mapOf(
                    "imports/test/job/conversions/1/normalized.json" to sampleConversionJson(id = "set1", time = "2025-01-01T01:00:00Z"),
                    "imports/test/job/conversions/2/normalized.json" to sampleConversionJson(id = "set2", time = "2025-01-01T01:05:00Z")
                ),
            )
        val (persist, rollback) = buildPersistAndRollback(historyRepo, liftRepo)
        val useCase =
            RunFullHistoricalImportUseCase(
                startHistoricalImport = StartHistoricalImportUseCase(jobRepo),
                historicalImportPort = port,
                persistImportedHistory = persist,
                rollbackHistoricalImport = rollback,
                importJobRepository = jobRepo,
                mergeImportedHistory = MergeImportedHistoryUseCase(),
                importLiftResolutionLogger = testImportLiftResolutionLogger(),
                loggingPort = FakeLoggingPort(),
            )
        val fileA = ImportFileInput("a".toByteArray(), "a.csv")
        val fileB = ImportFileInput("b".toByteArray(), "b.csv")

        val firstRun =
            useCase.run(listOf(fileA, fileB)).toList()
        val failed = firstRun.last() as HistoricalImportEvent.Failed
        val resume = checkNotNull(failed.resume)
        assertEquals(2, port.uploads.size)

        val correctedB = ImportFileInput("fixed".toByteArray(), "b.csv")
        val resumedFiles = listOf(fileA, correctedB)
        val secondRun =
            useCase.run(
                files = resumedFiles,
                resume = resume.copy(files = resumedFiles),
            ).toList()

        assertTrue(secondRun.last() is HistoricalImportEvent.Completed)
        assertEquals(3, port.uploads.size)
        assertEquals(2, historyRepo.getAllSets().size)
    }

    private fun sampleConversionJson(id: String = "set1", time: String = "2025-01-01T01:00:00Z"): String =
        """
        {
          "sessions": [
            {
              "id": "s1",
              "startedAt": "2025-01-01T00:00:00Z",
              "endedAt": null,
              "sourceApp": null
            }
          ],
          "sets": [
            {
              "id": "$id",
              "sessionId": "s1",
              "exerciseNameRaw": "Squat",
              "liftId": null,
              "setIndex": 0,
              "weightAmount": 100,
              "weightUnit": "Lb",
              "reps": 5,
              "rir": null,
              "completedAt": "$time"
            }
          ]
        }
        """.trimIndent()

    private fun buildPersistAndRollback(
        historyRepo: InMemoryTrainingHistoryRepository,
        liftRepo: FakeLiftRepository,
    ): Pair<PersistImportedHistoryUseCase, RollbackHistoricalImportUseCase> {
        val persistence = FakeResponseProfilePersistencePort()
        val extractResponseProfile =
            ExtractResponseProfileUseCase(
                trainingHistoryRepository = historyRepo,
                liftRepository = liftRepo,
                liftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
                muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
                importAnalysisMetadataRepository = InMemoryMetadataRepository(),
                responseProfilePersistence = persistence,
                extractor = DefaultResponseObservationExtractor(),
                experimentRegistry = EmptyExperimentRegistry(),
            )
        val persist =
            PersistImportedHistoryUseCase(
                trainingHistoryRepository = historyRepo,
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
                        trainingHistoryRepository = historyRepo,
                        liftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
                        supersetAffinityRepository = InMemorySupersetRepository(),
                        muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
                        importAnalysisMetadataRepository = InMemoryMetadataRepository(),
                        liftRepository = liftRepo,
                    ),
                extractResponseProfile = extractResponseProfile,
                responseProfilePersistence = persistence,
                liftRepository = liftRepo,
            )
        val rollback =
            RollbackHistoricalImportUseCase(
                historyRepo,
                InMemoryLiftInsightsRepository(),
                InMemorySupersetRepository(),
                InMemoryVolumeRepository(),
                InMemoryMetadataRepository(),
                persistence,
                liftRepo,
            )
        return persist to rollback
    }

    private fun testImportLiftResolutionLogger(): ImportLiftResolutionLogger =
        ImportLiftResolutionLogger(FakeLoggingPort())

    private class InMemoryTrainingHistoryRepository : TrainingHistoryRepository {
        private var sessions: List<HistoricalSession> = emptyList()
        private var sets: List<LoggedSet> = emptyList()

        override suspend fun replaceAll(sessions: List<HistoricalSession>, sets: List<LoggedSet>) {
            this.sessions = sessions
            this.sets = sets
        }

        override suspend fun getAllSessions(): List<HistoricalSession> = sessions

        override suspend fun getAllSets(): List<LoggedSet> = sets

        override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
            sets.filter { it.completedAt in windowStart..windowEnd }
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

    private fun minimalSeedCatalog(): List<Lift> =
        List(50) { index ->
            Lift(
                id = LiftId("seed-filler-$index"),
                name = "Seed Filler $index",
                movementPatterns = setOf(MovementPattern.SquatPattern),
                primaryMuscles = emptySet(),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                allowWeightChangeBetweenSets = false,
                enabled = false,
                isCustom = false,
            )
        }
}
