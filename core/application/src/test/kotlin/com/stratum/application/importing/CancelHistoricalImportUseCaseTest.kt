package com.stratum.application.importing

import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeImportJobRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.importing.ImportJobStatus

class CancelHistoricalImportUseCaseTest {
    @Test
    @DisplayName("Given active local job when cancel then job is failed and history is rolled back")
    fun cancelFailsActiveJobAndRollsBackHistory() = runTest {
        val trainingLogRepo = FakeTrainingLogRepository()
        trainingLogRepo.replaceImportHistory(
            sessions =
                listOf(
                    WorkoutLoggingFixtures.importTrainingSession(
                        id = "s1",
                        startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                        endedAt = null,
                        sourceApp = null,
                    ),
                ),
            sets =
                listOf(
                    WorkoutLoggingFixtures.importLoggedSet(
                        id = "set1",
                        sessionId = "s1",
                        exerciseNameRaw = "Squat",
                        liftId = null,
                        completedAt = Instant.parse("2025-01-01T01:00:00Z"),
                    ),
                ),
        )
        val metadataRepo = FakeImportAnalysisMetadataRepository()
        val liftRepo = FakeLiftRepository()
        liftRepo.seedUsedHistoricalImport(true)
        val jobRepo = FakeImportJobRepository()
        val active = jobRepo.createJob().copy(status = ImportJobStatus.AgentRunning)
        jobRepo.updateJob(active)

        val rollback =
            RollbackHistoricalImportUseCase(
                trainingLogRepo,
                FakeLiftTrainingInsightsRepository(),
                FakeSupersetAffinityRepository(),
                FakeMuscleVolumeBaselineRepository(),
                metadataRepo,
                FakeResponseProfilePersistencePort(),
                liftRepo,
            )
        val refresh =
            RefreshPersistedImportStateUseCase(
                reconcileHistoricalImport = ReconcileHistoricalImportUseCase(metadataRepo, rollback),
                loadHistoricalImportSummary =
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
        val subject =
            CancelHistoricalImportUseCase(
                rollbackHistoricalImport = rollback,
                refreshPersistedImportState = refresh,
                importJobRepository = jobRepo,
            )

        subject()

        assertEquals(ImportJobStatus.Failed, jobRepo.getJob(active.id)?.status)
        assertEquals("Import cancelled.", jobRepo.getJob(active.id)?.errorMessage)
        assertNull(jobRepo.getActiveJob())
        assertTrue(trainingLogRepo.getImportSessions().isEmpty())
        assertTrue(trainingLogRepo.getImportSets().isEmpty())
        assertFalse(liftRepo.currentUsedHistoricalImport)
    }
}
