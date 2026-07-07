package com.stratum.domain.importing

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReconcileHistoricalImportUseCaseTest {
    @Test
    fun `clears orphan history and stale flag when analysis metadata is missing`() = runTest {
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
        val metadataRepo = InMemoryMetadataRepository()
        val liftRepo = FakeLiftRepository()
        liftRepo.seedUsedHistoricalImport(true)
        val rollback =
            RollbackHistoricalImportUseCase(
                trainingLogRepo,
                InMemoryLiftInsightsRepository(),
                InMemorySupersetRepository(),
                InMemoryVolumeRepository(),
                metadataRepo,
                FakeResponseProfilePersistencePort(),
                liftRepo,
            )
        val subject = ReconcileHistoricalImportUseCase(metadataRepo, rollback)

        assertFalse(subject())
        assertTrue(trainingLogRepo.getImportSessions().isEmpty())
        assertTrue(trainingLogRepo.getImportSets().isEmpty())
        assertFalse(liftRepo.currentUsedHistoricalImport)
    }

    @Test
    fun `reports completed import when analysis metadata exists`() = runTest {
        val metadataRepo = InMemoryMetadataRepository()
        metadataRepo.save(
            ImportAnalysisMetadata(
                windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                windowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                provenLiftIds = emptyList(),
                unmatchedExerciseNames = emptyList(),
                totalSessions = 1,
                totalSets = 1,
            ),
        )
        val rollback =
            RollbackHistoricalImportUseCase(
                FakeTrainingLogRepository(),
                InMemoryLiftInsightsRepository(),
                InMemorySupersetRepository(),
                InMemoryVolumeRepository(),
                metadataRepo,
                FakeResponseProfilePersistencePort(),
                FakeLiftRepository(),
            )
        val subject = ReconcileHistoricalImportUseCase(metadataRepo, rollback)

        assertTrue(subject())
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
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit

        override suspend fun getAll(): List<LiftTrainingInsights> = emptyList()

        override suspend fun getByLiftId(liftId: com.stratum.domain.lift.identity.LiftId): LiftTrainingInsights? =
            null
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
