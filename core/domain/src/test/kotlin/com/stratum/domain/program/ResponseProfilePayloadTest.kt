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
                volumeSnapshots = emptyList(),
                epochsAnalyzed = 0,
                analysisWindowStart = windowStart,
                analysisWindowEnd = windowEnd,
            )

        assertEquals(0, influence.size)
        assertEquals(0, informational.size)
        assertEquals(0, payload.volumeResponses.size)
        assertFalse(shouldIncludeResponseProfile(payload))
    }

    @Test
    @DisplayName("RP-S10-001 response profile included when epochs analyzed")
    fun rpS10_001_responseProfileIncludedWhenPresent() {
        val payload =
            ResponseProfilePayload(
                volumeResponses = emptyList(),
                liftEffectiveness = emptyList(),
                volumeSnapshots = emptyList(),
                informational = ResponseProfileInformational(emptyList(), emptyList()),
                epochsAnalyzed = 2,
                analysisWindowStart = windowStart,
                analysisWindowEnd = windowEnd,
            )

        assertTrue(shouldIncludeResponseProfile(payload))
    }

    @Test
    @DisplayName("RP-S10-006 empty profile omitted from generation context")
    fun rpS10_006_emptyProfileOmitted() {
        val payload =
            ResponseProfilePayload(
                volumeResponses = emptyList(),
                liftEffectiveness = emptyList(),
                volumeSnapshots = emptyList(),
                informational = ResponseProfileInformational(emptyList(), emptyList()),
                epochsAnalyzed = 0,
                analysisWindowStart = windowStart,
                analysisWindowEnd = windowEnd,
            )

        assertFalse(shouldIncludeResponseProfile(payload))
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
