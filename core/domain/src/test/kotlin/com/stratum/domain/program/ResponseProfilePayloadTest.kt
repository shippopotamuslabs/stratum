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
    }

    @Test
    @DisplayName("RP-S10-006 no observations omits responseProfile")
    fun rpS10_006_emptyProfileOmitsKey() {
        val payload =
            ResponseProfilePayload(
                volumeResponses = emptyList(),
                liftEffectiveness = emptyList(),
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
            promptBuilder = ProgramGenerationPromptBuilder(),
        )

        val json = useCase()

        assertTrue(json.contains("\"responseProfile\""))
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

    private class FakeVolumeRepository : com.stratum.domain.importing.MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<com.stratum.domain.importing.MuscleVolumeBaseline>) = Unit
        override suspend fun getAll(): List<com.stratum.domain.importing.MuscleVolumeBaseline> = emptyList()
    }

    private class FakeMetadataRepository : com.stratum.domain.importing.ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: com.stratum.domain.importing.ImportAnalysisMetadata) = Unit
        override suspend fun get(): com.stratum.domain.importing.ImportAnalysisMetadata? = null
        override suspend fun clear() = Unit
    }
}
