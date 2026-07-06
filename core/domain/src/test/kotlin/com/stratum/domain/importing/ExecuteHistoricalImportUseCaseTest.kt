package com.stratum.domain.importing

import app.cash.turbine.test
import com.stratum.core.testing.importing.FakeHistoricalImportRunnerPort
import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.lift.identity.LiftId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ExecuteHistoricalImportUseCaseTest {
    @Test
    @DisplayName("Given invalid files when executed then emits ValidationFailed without running import")
    fun validationFailed() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val subject = buildSubject(runner)

        subject(files = emptyList(), resume = null).test {
            val event = awaitItem()
            assertTrue(event is HistoricalImportOrchestrationEvent.ValidationFailed)
            assertEquals(ImportFileValidationResult.Invalid.NoFiles, (event as HistoricalImportOrchestrationEvent.ValidationFailed).reason)
            awaitComplete()
        }
        assertTrue(runner.invocations.isEmpty())
    }

    @Test
    @DisplayName("Given successful import when executed then emits progress and completed refresh")
    fun successfulImport() = runTest {
        val progress = ImportProgress(ImportProgressPhase.Uploading)
        val runner =
            FakeHistoricalImportRunnerPort(
                events =
                    listOf(
                        HistoricalImportEvent.Progress(progress),
                        HistoricalImportEvent.Completed(
                            ExtractTrainingInsightsSummary(
                                liftsWithInsights = 1,
                                supersetPairs = 0,
                                musclesWithBaselines = 1,
                            ),
                        ),
                    ),
            )
        val metadataRepo =
            FakeImportAnalysisMetadataRepository(
                ImportAnalysisMetadata(
                    windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                    windowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                    provenLiftIds = listOf(LiftId("squat")),
                    unmatchedExerciseNames = emptyList(),
                    totalSessions = 3,
                    totalSets = 30,
                ),
            )
        val subject = buildSubject(runner, metadataRepo)
        val files = listOf(ImportFileInput(bytes = byteArrayOf(1), fileName = "export.csv"))

        subject(files = files, resume = null).test {
            assertEquals(HistoricalImportOrchestrationEvent.Progress(progress), awaitItem())
            val completed = awaitItem() as HistoricalImportOrchestrationEvent.Completed
            assertTrue(completed.refresh.hasCompletedHistoricalImport)
            awaitComplete()
        }
        assertEquals(1, runner.invocations.size)
        assertEquals(files, runner.invocations.single().files)
    }

    @Test
    @DisplayName("Given failed import when executed then rolls back and emits Failed with refresh")
    fun failedImportRollsBack() = runTest {
        val resume =
            HistoricalImportResumeState(
                files = listOf(ImportFileInput(bytes = byteArrayOf(1), fileName = "a.csv")),
                completedChunks = emptyList(),
            )
        val runner =
            FakeHistoricalImportRunnerPort(
                events =
                    listOf(
                        HistoricalImportEvent.Failed(
                            message = "conversion failed",
                            resume = resume,
                        ),
                    ),
            )
        val liftRepo = FakeLiftRepository()
        liftRepo.seedUsedHistoricalImport(true)
        val subject = buildSubject(runner, FakeImportAnalysisMetadataRepository(), liftRepo)

        subject(
            files = listOf(ImportFileInput(bytes = byteArrayOf(1), fileName = "a.csv")),
            resume = null,
        ).test {
            val failed = awaitItem() as HistoricalImportOrchestrationEvent.Failed
            assertEquals("conversion failed", failed.message)
            assertEquals(resume, failed.resume)
            assertFalse(failed.refresh.hasCompletedHistoricalImport)
            awaitComplete()
        }
        assertFalse(liftRepo.currentUsedHistoricalImport)
    }

    @Test
    @DisplayName("Given resume state when executed then skips validation and forwards resume")
    fun resumeSkipsValidation() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val resume =
            HistoricalImportResumeState(
                files = listOf(ImportFileInput(bytes = byteArrayOf(1), fileName = "a.csv")),
                completedChunks = emptyList(),
            )
        val subject = buildSubject(runner)

        subject(files = emptyList(), resume = resume).test {
            val progress = awaitItem() as HistoricalImportOrchestrationEvent.Progress
            assertEquals(ImportProgressPhase.Uploading, progress.progress.phase)
            awaitItem() // Completed
            awaitComplete()
        }
        assertEquals(resume, runner.invocations.single().resume)
    }

    private fun buildSubject(
        runner: FakeHistoricalImportRunnerPort,
        metadataRepo: FakeImportAnalysisMetadataRepository = FakeImportAnalysisMetadataRepository(),
        liftRepo: FakeLiftRepository = FakeLiftRepository(),
    ): ExecuteHistoricalImportUseCase {
        val rollback =
            RollbackHistoricalImportUseCase(
                FakeTrainingLogRepository(),
                FakeLiftTrainingInsightsRepository(),
                FakeSupersetAffinityRepository(),
                FakeMuscleVolumeBaselineRepository(),
                metadataRepo,
                FakeResponseProfilePersistencePort(),
                liftRepo,
            )
        val reconcile = ReconcileHistoricalImportUseCase(metadataRepo, rollback)
        val refresh =
            RefreshPersistedImportStateUseCase(
                reconcile,
                LoadHistoricalImportSummaryUseCase(
                    metadataRepo,
                    FakeMuscleVolumeBaselineRepository(),
                    FakeLiftTrainingInsightsRepository(),
                    FakeSupersetAffinityRepository(),
                    FakeVolumeResponseObservationRepository(),
                    FakeLiftEffectivenessObservationRepository(),
                    FakeMuscleVolumeResponseSnapshotRepository(),
                    liftRepo,
                ),
            )
        return ExecuteHistoricalImportUseCase(
            validateImportFiles = ValidateImportFilesUseCase(),
            runHistoricalImport = RunHistoricalImportUseCase(runner),
            rollbackHistoricalImport = rollback,
            refreshPersistedImportState = refresh,
        )
    }
}
