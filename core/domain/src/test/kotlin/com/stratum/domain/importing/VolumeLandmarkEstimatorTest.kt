package com.stratum.domain.importing

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.lift.identity.LiftId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class VolumeLandmarkEstimatorTest {
    @Test
    @DisplayName("Given sparse weeks when estimating then falls back to priors with low confidence")
    fun sparseWeeksUsePriors() {
        val estimate =
            VolumeLandmarkEstimator.estimate(
                VolumeLandmarkEstimator.Input(
                    muscle = MuscleGroup.Chest,
                    weeklyPoints = emptyList(),
                    volumeResponse = null,
                    rirCoverage = 0.0,
                ),
            )

        assertEquals(LandmarkConfidenceTier.Low, estimate.confidenceTier)
        assertEquals(VolumeLandmarkPriors.forMuscle(MuscleGroup.Chest).mevMid, estimate.estimatedMev)
    }

    @Test
    @DisplayName("Given varied volume with progression when estimating then derives observational MEV below MRV")
    fun variedVolumeProducesMevBelowMrv() {
        val points = buildWeeklyPoints(weekCount = 12)
        val estimate =
            VolumeLandmarkEstimator.estimate(
                VolumeLandmarkEstimator.Input(
                    muscle = MuscleGroup.Chest,
                    weeklyPoints = points,
                    volumeResponse = positiveVolumeResponse(),
                    rirCoverage = 0.85,
                ),
            )

        assertTrue(estimate.estimatedMev <= estimate.estimatedMrv)
        assertTrue(estimate.confidenceTier != LandmarkConfidenceTier.Low)
    }

    @Test
    @DisplayName("Given implausible observed values when estimating then confidence is reduced")
    fun implausibleValuesReduceConfidence() {
        val points =
            buildWeeklyPoints(weekCount = 12).map {
                it.copy(hardSets = 80.0)
            }
        val estimate =
            VolumeLandmarkEstimator.estimate(
                VolumeLandmarkEstimator.Input(
                    muscle = MuscleGroup.Bicep,
                    weeklyPoints = points,
                    volumeResponse = null,
                    rirCoverage = 0.2,
                ),
            )

        assertEquals(LandmarkConfidenceTier.Low, estimate.confidenceTier)
    }

    @Test
    @DisplayName("Given still-progressing at observed volume ceiling when estimating then MRV blends toward prior")
    fun stillProgressingAtCeilingBlendsTowardPrior() {
        // Highest-volume week (18 sets) still shows a positive e1RM delta, and the volume response
        // has a positive correlation slope. The estimator should blend the observed max (18) toward
        // the population prior MRV mid rather than reporting 18 as a settled ceiling.
        val prior = VolumeLandmarkPriors.forMuscle(MuscleGroup.Chest)
        val points = buildClimbingAtCeilingPoints(weekCount = 12, maxHardSets = 18.0)
        val estimate =
            VolumeLandmarkEstimator.estimate(
                VolumeLandmarkEstimator.Input(
                    muscle = MuscleGroup.Chest,
                    weeklyPoints = points,
                    volumeResponse = positiveVolumeResponse(),
                    rirCoverage = 0.85,
                ),
            )

        assertTrue(estimate.estimatedMrv > 18.0, "MRV should exceed observed max when still progressing")
        assertTrue(
            estimate.estimatedMrv <= prior.mrvMid + 0.001,
            "MRV should not exceed the population prior mid ($prior.mrvMid, got ${estimate.estimatedMrv})",
        )
    }

    @Test
    @DisplayName("Given higher effective volume on weeks without anchor e1RM when estimating then MRV uses that ceiling")
    fun mrvUsesEffectiveVolumeWithoutAnchorWeek() {
        val start = Instant.parse("2025-01-06T00:00:00Z")
        val points =
            (0 until 12).map { index ->
                val curlOnlyWeek = index < 8
                MuscleWeeklyVolumePoint(
                    weekStart = start.plusSeconds(index * 7L * 24L * 3600L),
                    hardSets = if (curlOnlyWeek) 11.0 else 20.0,
                    anchorPeakE1rm = if (curlOnlyWeek) 100.0 + index else null,
                    weekOverWeekE1rmDelta = if (index == 0 || !curlOnlyWeek) null else 1.0,
                )
            }
        val estimate =
            VolumeLandmarkEstimator.estimate(
                VolumeLandmarkEstimator.Input(
                    muscle = MuscleGroup.Bicep,
                    weeklyPoints = points,
                    volumeResponse = null,
                    rirCoverage = 0.5,
                ),
            )

        assertTrue(
            estimate.estimatedMrv > 11.0,
            "MRV should exceed curl-only ceiling when row-only weeks carry higher effective volume",
        )
    }

    private fun buildClimbingAtCeilingPoints(weekCount: Int, maxHardSets: Double): List<MuscleWeeklyVolumePoint> {
        val start = Instant.parse("2025-01-06T00:00:00Z")
        var previousPeak = 200.0
        return (0 until weekCount).map { index ->
            // Climb hard sets so the final week reaches maxHardSets and still has a positive delta.
            val hardSets = (8.0 + index * ((maxHardSets - 8.0) / (weekCount - 1)))
            val peak = previousPeak + 1.0
            val delta = if (index == 0) null else 1.0
            previousPeak = peak
            MuscleWeeklyVolumePoint(
                weekStart = start.plusSeconds(index * 7L * 24L * 3600L),
                hardSets = hardSets,
                anchorPeakE1rm = peak,
                weekOverWeekE1rmDelta = delta,
            )
        }
    }

    private fun buildWeeklyPoints(weekCount: Int): List<MuscleWeeklyVolumePoint> {
        val start = Instant.parse("2025-01-06T00:00:00Z")
        var previousPeak = 200.0
        return (0 until weekCount).map { index ->
            val hardSets = (8 + (index % 4) * 2).toDouble()
            val peak = previousPeak + 1.0
            val delta = if (index == 0) null else 1.0
            previousPeak = peak
            MuscleWeeklyVolumePoint(
                weekStart = start.plusSeconds(index * 7L * 24L * 3600L),
                hardSets = hardSets,
                anchorPeakE1rm = peak,
                weekOverWeekE1rmDelta = delta,
            )
        }
    }

    private fun positiveVolumeResponse(): VolumeResponseObservation =
        VolumeResponseObservation(
            muscle = MuscleGroup.Chest,
            epochId = EpochId("epoch-1"),
            anchorLiftId = LiftId("bench-press"),
            stimulus =
                StimulusDescriptor(
                    epochId = EpochId("epoch-1"),
                    experimentId = null,
                    muscle = MuscleGroup.Chest,
                    pattern = null,
                    liftId = LiftId("bench-press"),
                    weeklyHardSets = 12.0,
                    sessionsPerWeek = 2.0,
                    intensityProxy = null,
                ),
            outcome =
                OutcomeDescriptor(
                    anchorLiftId = LiftId("bench-press"),
                    metric = OutcomeMetric.WeeklyPeakE1rm,
                    value = 225.0,
                    windowWeeks = 12,
                    reliability = RegressionReliability(rSquared = 0.8, outlierCount = 0, completenessScore = 0.9),
                ),
            correlationSlope = 1.2,
            confidenceScore = 0.85,
            evidence = EvidenceLevel.Hypothesis,
            weeklyPointsUsed = 10,
            epochWeeksUsed = 12,
            observedWeeklySetRange = WeeklySetRange(min = 8.0, max = 18.0),
            bestObservedWeeklySetRange = null,
        )
}
