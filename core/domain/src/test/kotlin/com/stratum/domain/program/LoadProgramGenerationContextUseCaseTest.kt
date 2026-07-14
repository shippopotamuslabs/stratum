package com.stratum.domain.program

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.program.generation.context.VolumeSnapshotSource
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.OutcomeModelType
import com.stratum.domain.response.modeling.VolumePosture
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoadProgramGenerationContextUseCaseTest {
    @Test
    @DisplayName("RP-S10-007 load context includes volume responses when persisted")
    fun rpS10_007_loadContextIncludesVolumeResponses() = runTest {
        val volumeRepo = FakeVolumeResponseObservationRepository()
        val metadataRepo = FakeResponseProfileMetadataRepository()
        val epochId = EpochId("epoch-1")
        volumeRepo.seed(
            listOf(
                VolumeResponseObservation(
                    muscle = MuscleGroup.Quad,
                    epochId = epochId,
                    anchorLiftId = LiftId("lift-squat"),
                    stimulus =
                        StimulusDescriptor(
                            epochId = epochId,
                            experimentId = null,
                            muscle = MuscleGroup.Quad,
                            pattern = null,
                            liftId = LiftId("lift-squat"),
                            weeklyHardSets = 10.0,
                            sessionsPerWeek = 2.0,
                            intensityProxy = 3.0,
                        ),
                    outcome =
                        OutcomeDescriptor(
                            anchorLiftId = LiftId("lift-squat"),
                            metric = OutcomeMetric.EstimatedOneRmTrend,
                            value = 0.8,
                            windowWeeks = 10,
                            reliability = RegressionReliability(rSquared = 0.7, outlierCount = 0, completenessScore = 0.9),
                        ),
                    correlationSlope = 0.8,
                    confidenceScore = 0.80,
                    evidence = EvidenceLevel.Proven,
                    weeklyPointsUsed = 5,
                    epochWeeksUsed = 10,
                    observedWeeklySetRange = WeeklySetRange(min = 8.0, max = 12.0),
                    bestObservedWeeklySetRange = null,
                ),
            ),
        )
        metadataRepo.save(
            com.stratum.domain.response.ResponseProfileMetadata(
                epochsAnalyzed = 2,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                lastExtractedAt = Instant.parse("2026-01-15T00:00:00Z"),
            ),
        )
        val useCase = buildUseCase(
            volumeResponseObservationRepository = volumeRepo,
            responseProfileMetadataRepository = metadataRepo,
        )

        val context = useCase()

        assertTrue(context.volumeCorrelationsByMuscle.containsKey("Quad"))
        assertEquals("Proven", context.volumeCorrelationsByMuscle.getValue("Quad").evidence)
    }

    @Test
    @DisplayName("RP-S10-009 load context includes personal volume snapshots when baselines exist")
    fun rpS10_009_loadContextIncludesVolumeSnapshots() = runTest {
        val metadataRepo = FakeResponseProfileMetadataRepository()
        metadataRepo.save(
            com.stratum.domain.response.ResponseProfileMetadata(
                epochsAnalyzed = 1,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                lastExtractedAt = Instant.parse("2026-01-15T00:00:00Z"),
            ),
        )
        val volumeRepository = FakeVolumeRepository(
            listOf(
                MuscleVolumeBaseline(
                    muscle = MuscleGroup.Quad,
                    meanWeeklyHardSets = 10.0,
                    p90WeeklyHardSets = 14.0,
                    trendSlope = 0.5,
                ),
            ),
        )
        val importMetadataRepository = FakeMetadataRepository(
            ImportAnalysisMetadata(
                windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                windowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                provenLiftIds = listOf(LiftId("lift-squat")),
                unmatchedExerciseNames = emptyList(),
                totalSessions = 20,
                totalSets = 200,
            ),
        )
        val snapshotRepo = FakeMuscleVolumeResponseSnapshotRepository().apply {
            seed(
                listOf(
                    sampleSnapshot(MuscleGroup.Quad),
                ),
            )
        }
        val useCase = buildUseCase(
            muscleVolumeBaselineRepository = volumeRepository,
            importAnalysisMetadataRepository = importMetadataRepository,
            responseProfileMetadataRepository = metadataRepo,
            muscleVolumeResponseSnapshotRepository = snapshotRepo,
        )

        val context = useCase()
        val quadSnapshot = context.muscleVolumeSnapshotsByMuscle.getValue("Quad")

        assertEquals(6.0, quadSnapshot.retentionFloorMv)
        assertEquals(18.0, quadSnapshot.recoverabilityCeilingRc)
        assertEquals(VolumeSnapshotSource.Personal, quadSnapshot.source)
        assertTrue(context.muscleVolumeSnapshotsByMuscle.values.any { it.source == VolumeSnapshotSource.Literature })
    }

    @Test
    @DisplayName("RP-S10-008 load context forwards increment tiers and estimated 1RM onto LiftContext")
    fun rpS10_008_loadContextForwardsIncrementAndOneRm() = runTest {
        val liftId = LiftId("lift-bench")
        val lift = com.stratum.domain.lift.Lift(
            id = liftId,
            name = "Barbell Bench Press",
            movementPatterns = setOf(com.stratum.domain.lift.MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep),
            incrementTiers = listOf(
                com.stratum.domain.lift.IncrementTier(
                    lowerBound = com.stratum.domain.lift.Weight(0.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
                    upperBound = null,
                    increment = com.stratum.domain.lift.Weight(5.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
                ),
            ),
            presetTags = emptySet(),
            enabled = true,
        )
        val insights = listOf(
            com.stratum.domain.importing.LiftTrainingInsights(
                liftId = liftId,
                estimatedOneRm = com.stratum.domain.lift.Weight(250.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
                fatigueDecaySlope = -2.0,
                fatigueDecayIntercept = 0.0,
                sessionCount = 5,
                progressTrend = null,
                lastPerformedAt = null,
            ),
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(lift))
        val insightsRepo = FakeLiftInsightsRepository(insights)
        val useCase = LoadProgramGenerationContextUseCase(
            userTrainingProfileRepository = profileRepoWithSchedule(),
            observeSpecialization = ObserveSpecializationUseCase(FakeSpecializationPreferenceRepository()),
            liftRepository = liftRepo,
            liftTrainingInsightsRepository = insightsRepo,
            importAnalysisMetadataRepository = FakeMetadataRepository(),
            volumeResponseObservationRepository = FakeVolumeResponseObservationRepository(),
            liftEffectivenessObservationRepository = FakeLiftEffectivenessObservationRepository(),
            responseProfileMetadataRepository = FakeResponseProfileMetadataRepository(),
            muscleVolumeResponseSnapshotRepository = FakeMuscleVolumeResponseSnapshotRepository(),
            muscleVolumeBaselineRepository = FakeVolumeRepository(),
        )

        val context = useCase()
        val bench = context.liftsById.getValue(liftId.value)

        assertEquals(1, bench.incrementTiers.size, "Increment tiers should round-trip onto LiftContext")
        assertEquals(5.0, bench.incrementTiers.single().increment.amount)
        assertEquals(250.0, bench.estimatedOneRm?.amount, "Estimated 1RM should round-trip onto LiftContext")
        assertEquals(-2.0, bench.fatigueDecaySlope, "Fatigue slope should still round-trip")
    }

    private fun buildUseCase(
        volumeResponseObservationRepository: FakeVolumeResponseObservationRepository =
            FakeVolumeResponseObservationRepository(),
        responseProfileMetadataRepository: FakeResponseProfileMetadataRepository =
            FakeResponseProfileMetadataRepository(),
        muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository = FakeVolumeRepository(),
        importAnalysisMetadataRepository: ImportAnalysisMetadataRepository = FakeMetadataRepository(),
        muscleVolumeResponseSnapshotRepository: FakeMuscleVolumeResponseSnapshotRepository =
            FakeMuscleVolumeResponseSnapshotRepository(),
    ): LoadProgramGenerationContextUseCase =
        LoadProgramGenerationContextUseCase(
            userTrainingProfileRepository = profileRepoWithSchedule(),
            observeSpecialization = ObserveSpecializationUseCase(FakeSpecializationPreferenceRepository()),
            liftRepository = FakeLiftRepository(),
            liftTrainingInsightsRepository = FakeLiftInsightsRepository(),
            importAnalysisMetadataRepository = importAnalysisMetadataRepository,
            volumeResponseObservationRepository = volumeResponseObservationRepository,
            liftEffectivenessObservationRepository = FakeLiftEffectivenessObservationRepository(),
            responseProfileMetadataRepository = responseProfileMetadataRepository,
            muscleVolumeResponseSnapshotRepository = muscleVolumeResponseSnapshotRepository,
            muscleVolumeBaselineRepository = muscleVolumeBaselineRepository,
        )

    private fun profileRepoWithSchedule(): FakeUserTrainingProfileRepository {
        val repo = FakeUserTrainingProfileRepository()
        repo.seed(
            com.stratum.domain.onboarding.UserTrainingProfile(
                generationTrainingDayCount = 4,
                generationCycleLength = 7,
                generationRestSlotIndices = setOf(1, 3, 5),
                sessionLengthMinutes = 60,
            ),
        )
        return repo
    }

    private fun sampleSnapshot(muscle: MuscleGroup): MuscleVolumeResponseSnapshot =
        MuscleVolumeResponseSnapshot(
            muscle = muscle,
            retentionFloorMv = 6.0,
            paretoVolumePv = 12.0,
            recoverabilityCeilingRc = 18.0,
            growthEntryFloor = 6.0,
            observedDoseMin = 4.0,
            observedDoseMax = 16.0,
            observedDoseMedian = 10.0,
            productiveBandMin = 8.0,
            productiveBandMax = 14.0,
            posture = VolumePosture.Holding,
            pvInterval = LandmarkConfidenceInterval.Unavailable,
            confidenceTier = LandmarkConfidenceTier.Medium,
            outcomeModelType = OutcomeModelType.MuscleIndex,
            diagnosticAnchorLiftId = LiftId("lift-squat"),
        )

    private class FakeLiftInsightsRepository(
        private val insights: List<com.stratum.domain.importing.LiftTrainingInsights> = emptyList(),
    ) : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<com.stratum.domain.importing.LiftTrainingInsights>) = Unit

        override suspend fun getAll(): List<com.stratum.domain.importing.LiftTrainingInsights> = insights

        override suspend fun getByLiftId(liftId: LiftId): com.stratum.domain.importing.LiftTrainingInsights? =
            insights.firstOrNull { it.liftId == liftId }
    }

    private class FakeVolumeRepository(
        private val baselines: List<MuscleVolumeBaseline> = emptyList(),
    ) : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit

        override suspend fun getAll(): List<MuscleVolumeBaseline> = baselines
    }

    private class FakeMetadataRepository(
        private val metadata: ImportAnalysisMetadata? = null,
    ) : ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) = Unit

        override suspend fun get(): ImportAnalysisMetadata? = metadata

        override suspend fun clear() = Unit
    }
}
