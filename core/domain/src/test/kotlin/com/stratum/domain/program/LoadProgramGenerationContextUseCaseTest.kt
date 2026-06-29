package com.stratum.domain.program

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeLandmarkEstimateRepository
import com.stratum.core.testing.response.FakeResponseProfileMetadataRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.program.generation.context.VolumeLandmarkSource
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.modeling.MrvSource
import com.stratum.domain.response.modeling.MuscleVolumeLandmarkEstimate
import com.stratum.domain.response.modeling.OutcomeModelType
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

        assertTrue(context.volumeResponsesByMuscle.containsKey("Quad"))
        assertEquals("Proven", context.volumeResponsesByMuscle.getValue("Quad").evidence)
    }

    @Test
    @DisplayName("RP-S10-009 load context includes personal volume landmarks when baselines exist")
    fun rpS10_009_loadContextIncludesVolumeLandmarks() = runTest {
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
        val landmarkRepo = FakeMuscleVolumeLandmarkEstimateRepository().apply {
            seed(
                listOf(
                    MuscleVolumeLandmarkEstimate(
                        muscle = MuscleGroup.Quad,
                        estimatedMev = 6.0,
                        estimatedMav = 12.0,
                        estimatedMrv = 18.0,
                        estimatedPv = 9.0,
                        mevInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                        mavInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                        mrvInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                        pvInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                        confidenceTier = com.stratum.domain.importing.LandmarkConfidenceTier.Medium,
                        outcomeModelType = OutcomeModelType.MuscleIndex,
                        mrvSource = MrvSource.Observed,
                        observedDoseMin = 4.0,
                        observedDoseMax = 16.0,
                        diagnosticAnchorLiftId = LiftId("lift-squat"),
                    ),
                ),
            )
        }
        val useCase = buildUseCase(
            muscleVolumeBaselineRepository = volumeRepository,
            importAnalysisMetadataRepository = importMetadataRepository,
            responseProfileMetadataRepository = metadataRepo,
            muscleVolumeLandmarkEstimateRepository = landmarkRepo,
        )

        val context = useCase()
        val quadLandmark = context.volumeLandmarksByMuscle.getValue("Quad")

        assertEquals(6.0, quadLandmark.estimatedMev)
        assertEquals(18.0, quadLandmark.estimatedMrv)
        assertEquals(VolumeLandmarkSource.Personal, quadLandmark.source)
        assertTrue(context.volumeLandmarksByMuscle.values.any { it.source == VolumeLandmarkSource.Literature })
    }

    private fun buildUseCase(
        volumeResponseObservationRepository: FakeVolumeResponseObservationRepository =
            FakeVolumeResponseObservationRepository(),
        responseProfileMetadataRepository: FakeResponseProfileMetadataRepository =
            FakeResponseProfileMetadataRepository(),
        muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository = FakeVolumeRepository(),
        importAnalysisMetadataRepository: ImportAnalysisMetadataRepository = FakeMetadataRepository(),
        muscleVolumeLandmarkEstimateRepository: FakeMuscleVolumeLandmarkEstimateRepository =
            FakeMuscleVolumeLandmarkEstimateRepository(),
    ): LoadProgramGenerationContextUseCase =
        LoadProgramGenerationContextUseCase(
            userTrainingProfileRepository = FakeUserTrainingProfileRepository(),
            observeSpecialization = ObserveSpecializationUseCase(FakeSpecializationPreferenceRepository()),
            liftRepository = FakeLiftRepository(),
            liftTrainingInsightsRepository = FakeLiftInsightsRepository(),
            importAnalysisMetadataRepository = importAnalysisMetadataRepository,
            volumeResponseObservationRepository = volumeResponseObservationRepository,
            liftEffectivenessObservationRepository = FakeLiftEffectivenessObservationRepository(),
            responseProfileMetadataRepository = responseProfileMetadataRepository,
            muscleVolumeLandmarkEstimateRepository = muscleVolumeLandmarkEstimateRepository,
            muscleVolumeBaselineRepository = muscleVolumeBaselineRepository,
        )

    private class FakeLiftInsightsRepository : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<com.stratum.domain.importing.LiftTrainingInsights>) = Unit

        override suspend fun getAll(): List<com.stratum.domain.importing.LiftTrainingInsights> = emptyList()

        override suspend fun getByLiftId(liftId: LiftId): com.stratum.domain.importing.LiftTrainingInsights? = null
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
