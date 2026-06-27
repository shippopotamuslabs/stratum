package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.modeling.MrvSource
import com.stratum.domain.response.modeling.MuscleVolumeLandmarkEstimate
import com.stratum.domain.response.modeling.OutcomeModelType
import com.stratum.domain.response.identity.EpochId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResponseProfilePayloadTest {
    private val anchorLiftId = LiftId("lift-squat")
    private val epochId = EpochId("epoch-1")
    private val windowStart = Instant.parse("2025-01-01T00:00:00Z")
    private val windowEnd = Instant.parse("2025-06-01T00:00:00Z")

    @Test
    @DisplayName("RP-S10-002 Proven in volumeResponses influence")
    fun rpS10_002_provenInVolumeInfluence() {
        val observation = volumeObservation(muscle = MuscleGroup.Quad, evidence = EvidenceLevel.Proven, confidence = 0.80)
        val influence = filterVolumeInfluence(listOf(observation))

        assertEquals(1, influence.size)
        assertEquals(EvidenceLevel.Proven, influence[0].evidence)
        assertEquals(MuscleGroup.Quad, influence[0].muscle)
    }

    @Test
    @DisplayName("RP-S10-003 Hypothesis at 0.65 in influence")
    fun rpS10_003_hypothesisAtThresholdInInfluence() {
        val observation = volumeObservation(evidence = EvidenceLevel.Hypothesis, confidence = 0.65)
        val influence = filterVolumeInfluence(listOf(observation))

        assertEquals(1, influence.size)
        assertEquals(0.65, influence[0].confidenceScore)
    }

    @Test
    @DisplayName("RP-S10-004 Hypothesis below threshold excluded from influence")
    fun rpS10_004_lowHypothesisInformationalOnly() {
        val observation = volumeObservation(evidence = EvidenceLevel.Hypothesis, confidence = 0.64)
        val influence = filterVolumeInfluence(listOf(observation))
        val informational = filterInformationalVolume(listOf(observation))

        assertEquals(0, influence.size)
        assertEquals(1, informational.size)
        assertEquals(0.64, informational[0].confidenceScore)
    }

    @Test
    @DisplayName("RP-S10-005 InsufficientData omitted entirely")
    fun rpS10_005_insufficientDataOmitted() {
        val observation = volumeObservation(evidence = EvidenceLevel.InsufficientData, confidence = 0.10)
        val influence = filterVolumeInfluence(listOf(observation))
        val informational = filterInformationalVolume(listOf(observation))
        val payload =
            ResponseProfilePayload.fromObservations(
                volumeObservations = listOf(observation),
                effectivenessObservations = emptyList(),
                volumeLandmarks = emptyList(),
                epochsAnalyzed = 1,
                analysisWindowStart = windowStart,
                analysisWindowEnd = windowEnd,
            )
        val builder = ProgramGenerationPromptBuilder()
        val json =
            builder.buildPayload(
                profile = com.stratum.domain.onboarding.UserTrainingProfile.Empty,
                specialization = com.stratum.domain.onboarding.SpecializationPreference.Empty,
                provenLifts = emptyList(),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
                responseProfile = payload,
            )

        assertEquals(0, influence.size)
        assertEquals(0, informational.size)
        assertFalse(json.contains("InsufficientData"))
    }

    private fun volumeObservation(
        muscle: MuscleGroup = MuscleGroup.Quad,
        evidence: EvidenceLevel,
        confidence: Double,
    ): VolumeResponseObservation =
        VolumeResponseObservation(
            muscle = muscle,
            epochId = epochId,
            anchorLiftId = anchorLiftId,
            stimulus =
                StimulusDescriptor(
                    epochId = epochId,
                    experimentId = null,
                    muscle = muscle,
                    pattern = null,
                    liftId = anchorLiftId,
                    weeklyHardSets = 10.0,
                    sessionsPerWeek = 2.0,
                    intensityProxy = 3.0,
                ),
            outcome =
                OutcomeDescriptor(
                    anchorLiftId = anchorLiftId,
                    metric = OutcomeMetric.EstimatedOneRmTrend,
                    value = 0.8,
                    windowWeeks = 10,
                    reliability = RegressionReliability(rSquared = 0.7, outlierCount = 0, completenessScore = 0.9),
                ),
            correlationSlope = 0.8,
            confidenceScore = confidence,
            evidence = evidence,
            weeklyPointsUsed = 5,
            epochWeeksUsed = 10,
            observedWeeklySetRange = WeeklySetRange(min = 8.0, max = 12.0),
            bestObservedWeeklySetRange = null,
        )
}

class ProgramGenerationPromptBuilderResponseProfileTest {
    private val builder = ProgramGenerationPromptBuilder()

    @Test
    @DisplayName("RP-S10-001 JSON contains responseProfile")
    fun rpS10_001_jsonContainsResponseProfile() {
        val payload =
            ResponseProfilePayload(
                volumeResponses = emptyList(),
                liftEffectiveness = emptyList(),
                volumeLandmarks = emptyList(),
                informational = ResponseProfileInformational(emptyList(), emptyList()),
                epochsAnalyzed = 2,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
            )
        val json =
            builder.buildPayload(
                profile = com.stratum.domain.onboarding.UserTrainingProfile.Empty,
                specialization = com.stratum.domain.onboarding.SpecializationPreference.Empty,
                provenLifts = emptyList(),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
                responseProfile = payload,
            )

        assertTrue(json.contains("\"responseProfile\""))
        assertTrue(json.contains("\"epochsAnalyzed\":2"))
        kotlinx.serialization.json.Json.parseToJsonElement(json)
    }

    @Test
    @DisplayName("RP-S10-006 no observations omits responseProfile")
    fun rpS10_006_emptyProfileOmitsKey() {
        val payload =
            ResponseProfilePayload(
                volumeResponses = emptyList(),
                liftEffectiveness = emptyList(),
                volumeLandmarks = emptyList(),
                informational = ResponseProfileInformational(emptyList(), emptyList()),
                epochsAnalyzed = 0,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
            )
        val json =
            builder.buildPayload(
                profile = com.stratum.domain.onboarding.UserTrainingProfile.Empty,
                specialization = com.stratum.domain.onboarding.SpecializationPreference.Empty,
                provenLifts = emptyList(),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
                responseProfile = payload,
            )

        assertFalse(json.contains("\"responseProfile\""))
    }
}

class LoadProgramGenerationPayloadUseCaseTest {
    @Test
    @DisplayName("RP-S10-007 load payload assembly includes responseProfile when persisted")
    fun rpS10_007_loadPayloadIncludesResponseProfile() = kotlinx.coroutines.test.runTest {
        val volumeRepo = com.stratum.core.testing.response.FakeVolumeResponseObservationRepository()
        val metadataRepo = com.stratum.core.testing.response.FakeResponseProfileMetadataRepository()
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
        val useCase = LoadProgramGenerationPayloadUseCase(
            userTrainingProfileRepository = com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository(),
            observeSpecialization = com.stratum.domain.onboarding.ObserveSpecializationUseCase(
                com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository(),
            ),
            liftRepository = com.stratum.core.testing.onboarding.FakeLiftRepository(),
            liftTrainingInsightsRepository = FakeLiftInsightsRepository(),
            supersetAffinityRepository = FakeSupersetRepository(),
            muscleVolumeBaselineRepository = FakeVolumeRepository(),
            importAnalysisMetadataRepository = FakeMetadataRepository(),
            volumeResponseObservationRepository = volumeRepo,
            liftEffectivenessObservationRepository = com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository(),
            responseProfileMetadataRepository = metadataRepo,
            muscleVolumeLandmarkEstimateRepository = com.stratum.core.testing.response.FakeMuscleVolumeLandmarkEstimateRepository(),
            promptBuilder = ProgramGenerationPromptBuilder(),
        )

        val json = useCase()

        assertTrue(json.contains("\"responseProfile\""))
        kotlinx.serialization.json.Json.parseToJsonElement(json)
    }

    @Test
    @DisplayName("RP-S10-009 load payload includes volumeLandmarks (MEV/MRV) when baselines exist")
    fun rpS10_009_loadPayloadIncludesVolumeLandmarks() = kotlinx.coroutines.test.runTest {
        val volumeRepo = com.stratum.core.testing.response.FakeVolumeResponseObservationRepository()
        val metadataRepo = com.stratum.core.testing.response.FakeResponseProfileMetadataRepository()
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
                com.stratum.domain.importing.MuscleVolumeBaseline(
                    muscle = MuscleGroup.Quad,
                    meanWeeklyHardSets = 10.0,
                    p90WeeklyHardSets = 14.0,
                    trendSlope = 0.5,
                ),
            ),
        )
        val importMetadataRepository = FakeMetadataRepository(
            com.stratum.domain.importing.ImportAnalysisMetadata(
                windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                windowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                provenLiftIds = listOf(LiftId("lift-squat")),
                unmatchedExerciseNames = emptyList(),
                totalSessions = 20,
                totalSets = 200,
            ),
        )
        val liftRepository = com.stratum.core.testing.onboarding.FakeLiftRepository()
        val landmarkRepo = com.stratum.core.testing.response.FakeMuscleVolumeLandmarkEstimateRepository().apply {
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
        val useCase = LoadProgramGenerationPayloadUseCase(
            userTrainingProfileRepository = com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository(),
            observeSpecialization = com.stratum.domain.onboarding.ObserveSpecializationUseCase(
                com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository(),
            ),
            liftRepository = liftRepository,
            liftTrainingInsightsRepository = FakeLiftInsightsRepository(),
            supersetAffinityRepository = FakeSupersetRepository(),
            muscleVolumeBaselineRepository = volumeRepository,
            importAnalysisMetadataRepository = importMetadataRepository,
            volumeResponseObservationRepository = volumeRepo,
            liftEffectivenessObservationRepository = com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository(),
            responseProfileMetadataRepository = metadataRepo,
            muscleVolumeLandmarkEstimateRepository = landmarkRepo,
            promptBuilder = ProgramGenerationPromptBuilder(),
        )

        val json = useCase()

        assertTrue(json.contains("\"volumeLandmarks\":["))
        assertTrue(json.contains("\"estimatedMev\""))
        assertTrue(json.contains("\"estimatedMrv\""))
        kotlinx.serialization.json.Json.parseToJsonElement(json)
    }

    private class FakeLiftInsightsRepository : com.stratum.domain.importing.LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<com.stratum.domain.importing.LiftTrainingInsights>) = Unit
        override suspend fun getAll(): List<com.stratum.domain.importing.LiftTrainingInsights> = emptyList()
        override suspend fun getByLiftId(liftId: LiftId) = null
    }

    private class FakeSupersetRepository : com.stratum.domain.importing.SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<com.stratum.domain.importing.SupersetAffinity>) = Unit
        override suspend fun getAll(): List<com.stratum.domain.importing.SupersetAffinity> = emptyList()
    }

    private class FakeVolumeRepository(
        private val baselines: List<com.stratum.domain.importing.MuscleVolumeBaseline> = emptyList(),
    ) : com.stratum.domain.importing.MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<com.stratum.domain.importing.MuscleVolumeBaseline>) = Unit
        override suspend fun getAll(): List<com.stratum.domain.importing.MuscleVolumeBaseline> = baselines
    }

    private class FakeMetadataRepository(
        private val metadata: com.stratum.domain.importing.ImportAnalysisMetadata? = null,
    ) : com.stratum.domain.importing.ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: com.stratum.domain.importing.ImportAnalysisMetadata) = Unit
        override suspend fun get(): com.stratum.domain.importing.ImportAnalysisMetadata? = metadata
        override suspend fun clear() = Unit
    }
}
