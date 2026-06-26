package com.stratum.domain.response.modeling

import com.stratum.domain.importing.IntervalMethod
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MuscleDoseResponseModelTest {
    @Test
    @DisplayName("MDM-001 insufficient data falls back toward priors")
    fun insufficientDataUsesPriorBlend() {
        val pairing =
            LaggedDoseResponsePairer.PairingResult(
                points = emptyList(),
                selectedLagWeeks = 0,
                responseWindowWeeks = 4,
                predictiveScore = null,
            )
        val result =
            MuscleDoseResponseModel.estimate(
                muscle = MuscleGroup.Chest,
                pairing = pairing,
                indices = emptyList(),
                stimulus = emptyList(),
                diagnosticAnchorLiftId = null,
            )

        assertEquals(OutcomeModelType.PriorOnly, result.landmarks.outcomeModelType)
        assertEquals(MrvSource.PriorDominant, result.landmarks.mrvSource)
        assertTrue(result.landmarks.confidenceTier == LandmarkConfidenceTier.Low)
        assertTrue(result.landmarks.estimatedMev.isFinite())
        assertTrue(result.landmarks.estimatedMav.isFinite())
        assertTrue(result.landmarks.estimatedMrv.isFinite())
        assertTrue(result.landmarks.estimatedPv.isFinite())
        assertTrue(result.diagnostics.confidenceScore.isFinite())
    }

    @Test
    @DisplayName("MDM-003 empty outcome indices still produce persistable finite landmarks")
    fun emptyOutcomeIndicesProduceFiniteLandmarks() {
        val pairing =
            LaggedDoseResponsePairer.PairingResult(
                points = emptyList(),
                selectedLagWeeks = 0,
                responseWindowWeeks = 4,
                predictiveScore = null,
            )
        val result =
            MuscleDoseResponseModel.estimate(
                muscle = MuscleGroup.Tricep,
                pairing = pairing,
                indices = emptyList(),
                stimulus = emptyList(),
                diagnosticAnchorLiftId = null,
            )

        assertTrue(result.landmarks.estimatedMev.isFinite() && result.landmarks.estimatedMev > 0.0)
        assertTrue(result.landmarks.mevInterval.low.isFinite())
        assertTrue(result.landmarks.mevInterval.high.isFinite())
    }

    @Test
    @DisplayName("MDM-002 monotonic fit recovers landmarks from synthetic dose points")
    fun monotonicFitRecoversLandmarks() {
        val points =
            (0 until 12).map { index ->
                val dose = 6.0 + index
                val response = 0.01 - kotlin.math.abs(dose - 14.0) * 0.002
                MuscleDoseResponsePoint(
                    muscle = MuscleGroup.Chest,
                    weekStart = java.time.Instant.parse("2025-01-06T00:00:00Z").plusSeconds(index * 7L * 24 * 3600),
                    dose = dose,
                    response = response,
                    lagWeeks = 1,
                    responseWindowWeeks = 4,
                )
            }
        val pairing =
            LaggedDoseResponsePairer.PairingResult(
                points = points,
                selectedLagWeeks = 1,
                responseWindowWeeks = 4,
                predictiveScore = -0.1,
            )
        val indices =
            points.map {
                MuscleWeeklyOutcomeIndex(
                    muscle = MuscleGroup.Chest,
                    weekStart = it.weekStart,
                    indexValue = it.response,
                    indexStdError = 0.01,
                    contributingLiftCount = 2,
                    effectiveLiftCount = 2,
                    primaryCoverage = 0.8,
                    compositionHash = "bench:10|fly:5",
                    compositionDrift = 0.05,
                    qualityFlags = emptySet(),
                )
            }
        val stimulus =
            points.map {
                MuscleWeeklyStimulus(
                    muscle = MuscleGroup.Chest,
                    weekStart = it.weekStart,
                    hardSets = it.dose,
                    sessions = 2,
                    meanRir = 2.0,
                    intensityProxy = 0.8,
                    compositionHash = "bench:10|fly:5",
                    deloadFlag = false,
                    qualityFlags = emptySet(),
                )
            }
        val result =
            MuscleDoseResponseModel.estimate(
                muscle = MuscleGroup.Chest,
                pairing = pairing,
                indices = indices,
                stimulus = stimulus,
                diagnosticAnchorLiftId = null,
            )

        assertTrue(result.landmarks.estimatedMev <= result.landmarks.estimatedMrv)
        assertTrue(result.landmarks.estimatedPv <= result.landmarks.estimatedMav)
        assertTrue(result.landmarks.mevInterval.method == IntervalMethod.Pragmatic || result.landmarks.mevInterval.isAvailable)
    }
}
