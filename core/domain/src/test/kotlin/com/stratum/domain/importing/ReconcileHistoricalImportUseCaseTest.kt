package com.stratum.domain.importing

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReconcileHistoricalImportUseCaseTest {
    @Test
    fun `clears orphan history and stale flag when analysis metadata is missing`() = runTest {
        val historyRepo = InMemoryTrainingHistoryRepository(
            sessions =
                listOf(
                    HistoricalSession(
                        id = HistoricalSessionId("s1"),
                        startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                        endedAt = null,
                        sourceApp = null,
                    ),
                ),
            sets =
                listOf(
                    LoggedSet(
                        id = LoggedSetId("set1"),
                        sessionId = HistoricalSessionId("s1"),
                        exerciseNameRaw = "Squat",
                        liftId = null,
                        setIndex = 0,
                        weight = Weight(100.0, Weight.WeightUnit.Lb),
                        reps = 5,
                        rir = null,
                        completedAt = Instant.parse("2025-01-01T01:00:00Z"),
                    ),
                ),
        )
        val metadataRepo = InMemoryMetadataRepository()
        val liftRepo = FakeLiftRepository()
        liftRepo.seedUsedHistoricalImport(true)
        val rollback =
            RollbackHistoricalImportUseCase(
                historyRepo,
                InMemoryLiftInsightsRepository(),
                InMemorySupersetRepository(),
                InMemoryVolumeRepository(),
                metadataRepo,
                FakeResponseProfilePersistencePort(),
                liftRepo,
            )
        val subject = ReconcileHistoricalImportUseCase(metadataRepo, rollback)

        assertFalse(subject())
        assertTrue(historyRepo.getAllSessions().isEmpty())
        assertTrue(historyRepo.getAllSets().isEmpty())
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
                InMemoryTrainingHistoryRepository(),
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

    private class InMemoryTrainingHistoryRepository(
        private var sessions: List<HistoricalSession> = emptyList(),
        private var sets: List<LoggedSet> = emptyList(),
    ) : TrainingHistoryRepository {
        override suspend fun replaceAll(
            sessions: List<HistoricalSession>,
            sets: List<LoggedSet>,
        ) {
            this.sessions = sessions
            this.sets = sets
        }

        override suspend fun getAllSessions(): List<HistoricalSession> = sessions

        override suspend fun getAllSets(): List<LoggedSet> = sets

        override suspend fun getSetsInWindow(
            windowStart: Instant,
            windowEnd: Instant,
        ): List<LoggedSet> = emptyList()
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
