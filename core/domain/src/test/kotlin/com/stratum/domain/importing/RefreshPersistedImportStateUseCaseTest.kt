package com.stratum.domain.importing

import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.lift.Lift
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RefreshPersistedImportStateUseCaseTest {
    @Test
    @DisplayName("Given orphan import when refreshed then reports incomplete and no summary")
    fun clearsIncompleteImport() = runTest {
        val metadataRepo = FakeImportAnalysisMetadataRepository()
        val liftRepo = FakeLiftRepository()
        liftRepo.seedUsedHistoricalImport(true)
        val subject = buildSubject(metadataRepo = metadataRepo, liftRepo = liftRepo)

        val result = subject()

        assertFalse(result.hasCompletedHistoricalImport)
        assertNull(result.importInsightsSummary)
        assertFalse(liftRepo.currentUsedHistoricalImport)
    }

    @Test
    @DisplayName("Given completed import when refreshed then reports completed with summary")
    fun loadsSummaryWhenComplete() = runTest {
        val metadata =
            ImportAnalysisMetadata(
                windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                windowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                provenLiftIds = listOf(LiftId("squat")),
                unmatchedExerciseNames = emptyList(),
                totalSessions = 10,
                totalSets = 100,
            )
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                Lift(
                    id = LiftId("squat"),
                    name = "Squat",
                    movementPatterns = setOf(MovementPattern.SquatPattern),
                    primaryMuscles = setOf(MuscleGroup.Quad),
                    secondaryMuscles = emptySet(),
                    allowWeightChangeBetweenSets = false,
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = true,
                ),
            ),
        )
        val subject = buildSubject(metadataRepo = FakeImportAnalysisMetadataRepository(metadata), liftRepo = liftRepo)

        val result = subject()

        assertTrue(result.hasCompletedHistoricalImport)
        assertNotNull(result.importInsightsSummary)
        assertEquals(10, result.importInsightsSummary?.totalSessions)
    }

    private fun buildSubject(
        metadataRepo: FakeImportAnalysisMetadataRepository,
        liftRepo: FakeLiftRepository,
    ): RefreshPersistedImportStateUseCase {
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
        val loadSummary =
            LoadHistoricalImportSummaryUseCase(
                metadataRepo,
                FakeMuscleVolumeBaselineRepository(),
                FakeLiftTrainingInsightsRepository(),
                FakeSupersetAffinityRepository(),
                FakeVolumeResponseObservationRepository(),
                FakeLiftEffectivenessObservationRepository(),
                FakeMuscleVolumeResponseSnapshotRepository(),
                liftRepo,
            )
        return RefreshPersistedImportStateUseCase(reconcile, loadSummary)
    }
}
