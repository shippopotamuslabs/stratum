package com.stratum.domain.importing

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.VolumeResponseObservation
import java.time.Instant

data class MuscleWeeklyVolumePoint(
    val weekStart: Instant,
    val hardSets: Int,
    val anchorPeakE1rm: Double?,
    val weekOverWeekE1rmDelta: Double?,
)

data class EstimatedVolumeLandmark(
    val muscle: MuscleGroup,
    val estimatedMev: Double,
    val estimatedMrv: Double,
    val confidenceTier: LandmarkConfidenceTier,
)

/**
 * Estimates observational MEV/MRV from weekly hard-set and anchor-lift outcome series.
 * Not a controlled experiment — blends personal signal with literature priors when data
 * is sparse, noisy, or outside plausible population ranges.
 */
object VolumeLandmarkEstimator {
    data class Input(
        val muscle: MuscleGroup,
        val weeklyPoints: List<MuscleWeeklyVolumePoint>,
        val volumeResponse: VolumeResponseObservation?,
        val rirCoverage: Double,
        val prior: VolumeLandmarkPrior = VolumeLandmarkPriors.forMuscle(muscle),
    )

    fun estimate(input: Input): EstimatedVolumeLandmark {
        val prior = input.prior
        if (input.weeklyPoints.size < VolumeLandmarkEstimatorConstants.MIN_WEEKS_FOR_OBSERVATION) {
            return fallback(prior, input.muscle, LandmarkConfidenceTier.Low)
        }

        val points = input.weeklyPoints.filter { it.anchorPeakE1rm != null }.sortedBy { it.weekStart }
        if (points.size < VolumeLandmarkEstimatorConstants.MIN_WEEKS_FOR_OBSERVATION) {
            return fallback(prior, input.muscle, LandmarkConfidenceTier.Low)
        }

        val volumeSpread = points.map { it.hardSets }.distinct().size
        var observedMev = estimateMev(points)
        var observedMrv = estimateMrv(points, input.volumeResponse)
        var confidence =
            baseConfidence(
                weekCount = points.size,
                volumeSpread = volumeSpread,
                rirCoverage = input.rirCoverage,
                volumeResponse = input.volumeResponse,
            )

        if (!isPlausible(observedMev, prior.mevLow, prior.mevHigh)) {
            confidence *= VolumeLandmarkEstimatorConstants.IMPLAUSIBLE_CONFIDENCE_PENALTY
            observedMev = prior.mevMid
        }
        if (!isPlausible(observedMrv, prior.mrvLow, prior.mrvHigh)) {
            confidence *= VolumeLandmarkEstimatorConstants.IMPLAUSIBLE_CONFIDENCE_PENALTY
            observedMrv = prior.mrvMid
        }

        val weight = confidence.coerceIn(0.0, 1.0)
        val finalMev = (weight * observedMev) + ((1.0 - weight) * prior.mevMid)
        val finalMrv = (weight * observedMrv) + ((1.0 - weight) * prior.mrvMid)

        return EstimatedVolumeLandmark(
            muscle = input.muscle,
            estimatedMev = finalMev.coerceAtLeast(0.0),
            estimatedMrv = finalMrv.coerceAtLeast(finalMev),
            confidenceTier = confidenceToTier(confidence),
        )
    }

    private fun fallback(
        prior: VolumeLandmarkPrior,
        muscle: MuscleGroup,
        tier: LandmarkConfidenceTier,
    ): EstimatedVolumeLandmark =
        EstimatedVolumeLandmark(
            muscle = muscle,
            estimatedMev = prior.mevMid,
            estimatedMrv = prior.mrvMid,
            confidenceTier = tier,
        )

    private fun estimateMev(points: List<MuscleWeeklyVolumePoint>): Double {
        val sortedByVolume = points.sortedBy { it.hardSets }
        for (candidate in sortedByVolume) {
            val delta = candidate.weekOverWeekE1rmDelta
            if (delta == null || delta >= -VolumeLandmarkEstimatorConstants.STAGNATION_DELTA_LBS) {
                return candidate.hardSets.toDouble()
            }
        }
        return sortedByVolume.first().hardSets.toDouble()
    }

    private fun estimateMrv(
        points: List<MuscleWeeklyVolumePoint>,
        volumeResponse: VolumeResponseObservation?,
    ): Double {
        volumeResponse?.let { response ->
            if (response.correlationSlope > 0) {
                return response.observedWeeklySetRange.max
            }
            if (response.correlationSlope <= 0) {
                val plateauVolume =
                    points
                        .filter {
                            (it.weekOverWeekE1rmDelta ?: 0.0) <
                                -VolumeLandmarkEstimatorConstants.STAGNATION_DELTA_LBS
                        }
                        .minOfOrNull { it.hardSets }
                if (plateauVolume != null) {
                    return (
                        plateauVolume - VolumeLandmarkEstimatorConstants.PLATEAU_VOLUME_STEP_DOWN
                        ).coerceAtLeast(VolumeLandmarkEstimatorConstants.MIN_RECOVERABLE_WEEKLY_SETS)
                        .toDouble()
                }
            }
        }

        var bestVolume = points.maxOf { it.hardSets }.toDouble()
        for (candidate in points.sortedByDescending { it.hardSets }) {
            val delta = candidate.weekOverWeekE1rmDelta
            if (delta == null || delta >= -VolumeLandmarkEstimatorConstants.STAGNATION_DELTA_LBS) {
                bestVolume = candidate.hardSets.toDouble()
                break
            }
        }
        return bestVolume
    }

    private fun baseConfidence(
        weekCount: Int,
        volumeSpread: Int,
        rirCoverage: Double,
        volumeResponse: VolumeResponseObservation?,
    ): Double {
        var score = 0.0
        score +=
            (
                weekCount.coerceAtMost(VolumeLandmarkEstimatorConstants.CONFIDENCE_WEEK_CAP).toDouble() /
                    VolumeLandmarkEstimatorConstants.CONFIDENCE_WEEK_CAP.toDouble()
                ) * VolumeLandmarkEstimatorConstants.CONFIDENCE_WEEK_WEIGHT
        score +=
            (
                volumeSpread.coerceAtMost(VolumeLandmarkEstimatorConstants.CONFIDENCE_VOLUME_SPREAD_CAP).toDouble() /
                    VolumeLandmarkEstimatorConstants.CONFIDENCE_VOLUME_SPREAD_CAP.toDouble()
                ) * VolumeLandmarkEstimatorConstants.CONFIDENCE_VOLUME_SPREAD_WEIGHT
        score += rirCoverage.coerceIn(0.0, 1.0) * VolumeLandmarkEstimatorConstants.CONFIDENCE_RIR_WEIGHT
        volumeResponse?.let { response ->
            score +=
                response.outcome.reliability.rSquared?.coerceIn(0.0, 1.0)?.times(
                    VolumeLandmarkEstimatorConstants.CONFIDENCE_RSQUARED_WEIGHT,
                ) ?: 0.0
            score +=
                (
                    response.weeklyPointsUsed.coerceAtMost(
                        VolumeLandmarkEstimatorConstants.CONFIDENCE_WEEKLY_POINTS_CAP,
                    ).toDouble() / VolumeLandmarkEstimatorConstants.CONFIDENCE_WEEKLY_POINTS_CAP.toDouble()
                    ) * VolumeLandmarkEstimatorConstants.CONFIDENCE_WEEKLY_POINTS_WEIGHT
        }
        return score.coerceIn(0.0, 1.0)
    }

    private fun isPlausible(value: Double, low: Double, high: Double): Boolean {
        val floor = low * VolumeLandmarkEstimatorConstants.PLAUSIBLE_LOW_MULTIPLIER
        val ceiling = high * VolumeLandmarkEstimatorConstants.PLAUSIBLE_HIGH_MULTIPLIER
        return value in floor..ceiling
    }

    private fun confidenceToTier(score: Double): LandmarkConfidenceTier =
        when {
            score >= VolumeLandmarkEstimatorConstants.HIGH_CONFIDENCE_THRESHOLD -> LandmarkConfidenceTier.High
            score >= VolumeLandmarkEstimatorConstants.MEDIUM_CONFIDENCE_THRESHOLD -> LandmarkConfidenceTier.Medium
            else -> LandmarkConfidenceTier.Low
        }
}
