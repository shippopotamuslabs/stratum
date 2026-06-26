package com.stratum.core.inference

import com.stratum.core.testing.importing.FakeHistoricalImportPort
import com.stratum.core.testing.importing.FakeImportJobRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.importing.Bm25LiftNameResolver
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.HistoricalImportEvent
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
import com.stratum.domain.importing.EnrichCustomLiftsUseCase
import com.stratum.domain.importing.PersistImportedHistoryUseCase
import com.stratum.core.testing.response.FakeExperimentBlockRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeResponseEpochRepository
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
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
            conversionJson =
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
                      "id": "set1",
                      "sessionId": "s1",
                      "exerciseNameRaw": "Squat",
                      "liftId": null,
                      "setIndex": 0,
                      "weightAmount": 100,
                      "weightUnit": "Lb",
                      "reps": 5,
                      "rir": null,
                      "completedAt": "2025-01-01T01:00:00Z"
                    }
                  ]
                }
                """.trimIndent(),
        )
        val epochRepo = FakeResponseEpochRepository()
        val volumeResponseRepo = FakeVolumeResponseObservationRepository()
        val effectivenessRepo = FakeLiftEffectivenessObservationRepository()
        val experimentRepo = FakeExperimentBlockRepository()
        val responseMetadataRepo = FakeResponseProfileMetadataRepository()
        val extractResponseProfile =
            ExtractResponseProfileUseCase(
                trainingHistoryRepository = historyRepo,
                liftRepository = liftRepo,
                liftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
                muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
                importAnalysisMetadataRepository = InMemoryMetadataRepository(),
                responseEpochRepository = epochRepo,
                volumeResponseObservationRepository = volumeResponseRepo,
                liftEffectivenessObservationRepository = effectivenessRepo,
                experimentBlockRepository = experimentRepo,
                responseProfileMetadataRepository = responseMetadataRepo,
                extractor = DefaultResponseObservationExtractor(),
                experimentRegistry = EmptyExperimentRegistry(),
            )
        val persist = PersistImportedHistoryUseCase(
            trainingHistoryRepository = historyRepo,
            resolveImportLifts =
                ResolveImportLiftsUseCase(
                    liftRepo,
                    Bm25LiftNameResolver(),
                    EnrichCustomLiftsUseCase(FakeCustomLiftInferencePort()),
                    FakeLiftEquipmentCatalog(),
                    FakeImportEquipmentInferencePort(),
                ),
            extractTrainingInsights = ExtractTrainingInsightsUseCase(
                trainingHistoryRepository = historyRepo,
                liftTrainingInsightsRepository = InMemoryLiftInsightsRepository(),
                supersetAffinityRepository = InMemorySupersetRepository(),
                muscleVolumeBaselineRepository = InMemoryVolumeRepository(),
                importAnalysisMetadataRepository = InMemoryMetadataRepository(),
                liftRepository = liftRepo,
            ),
            extractResponseProfile = extractResponseProfile,
            liftRepository = liftRepo,
        )
        val rollback =
            RollbackHistoricalImportUseCase(
                historyRepo,
                InMemoryLiftInsightsRepository(),
                InMemorySupersetRepository(),
                InMemoryVolumeRepository(),
                InMemoryMetadataRepository(),
                epochRepo,
                volumeResponseRepo,
                effectivenessRepo,
                experimentRepo,
                responseMetadataRepo,
                liftRepo,
            )
        val useCase = RunFullHistoricalImportUseCase(
            startHistoricalImport = StartHistoricalImportUseCase(jobRepo),
            historicalImportPort = port,
            persistImportedHistory = persist,
            rollbackHistoricalImport = rollback,
            importJobRepository = jobRepo,
        )

        val events = useCase.run("id,exercise_name\n1,Squat".toByteArray(), "export.csv").toList()

        assertTrue(events.filterIsInstance<HistoricalImportEvent.Progress>().isNotEmpty())
        assertTrue(events.last() is HistoricalImportEvent.Completed)
        assertTrue(port.uploads.isNotEmpty())
        assertEquals(1, jobRepo.allJobs().size)
        assertEquals(ImportJobStatus.Completed, jobRepo.allJobs().first().status)
    }

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
                movementPattern = MovementPattern.LegPush,
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
