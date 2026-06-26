package com.stratum.domain.importing

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.VolumeResponseObservation
import java.time.Instant

data class MuscleWeeklyVolumePoint(
    val weekStart: Instant,
    val hardSets: Double,
    val anchorPeakE1rm: Double?,
    val weekOverWeekE1rmDelta: Double?,
)

data class EstimatedVolumeLandmark(
    val muscle: MuscleGroup,
    val estimatedMev: Double,
    val estimatedMav: Double,
    val estimatedMrv: Double,
    val estimatedPv: Double,
    val mevInterval: LandmarkConfidenceInterval,
    val mavInterval: LandmarkConfidenceInterval,
    val mrvInterval: LandmarkConfidenceInterval,
    val pvInterval: LandmarkConfidenceInterval,
    val confidenceTier: LandmarkConfidenceTier,
)

/**
 * Estimates observational MEV/MAV/MRV/PV from weekly hard-set and anchor-lift outcome series.
 * Not a controlled experiment — blends personal signal with literature priors when data
 * is sparse, noisy, or outside plausible population ranges.
 *
 * When enough anchored weeks with sufficient volume spread exist, a quadratic OLS dose-response
 * fit derives MAV (vertex), MEV (lower root), and PV (Pareto point) with Delta Method confidence
 * intervals, and a Banister fitness-fatigue fit derives MRV with a residual-bootstrap CI. When
 * data is too sparse for those fits, the existing heuristic MEV/MRV path runs and a pragmatic
 * interval is derived from the confidence tier and prior band width.
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
        val weeklyPoints = input.weeklyPoints.sortedBy { it.weekStart }
        val anchoredPoints = weeklyPoints.filter { it.anchorPeakE1rm != null }
        if (anchoredPoints.size < VolumeLandmarkEstimatorConstants.MIN_WEEKS_FOR_OBSERVATION) {
            return fallback(prior, input.muscle, LandmarkConfidenceTier.Low)
        }

        val volumePoints = weeklyPoints.filter { it.hardSets > 0.0 }
        val volumeSpread = volumePoints.map { it.hardSets }.distinct().size
        val dosePoints =
            anchoredPoints.mapNotNull { p ->
                val delta = p.weekOverWeekE1rmDelta ?: return@mapNotNull null
                VolumeDosePoint(volume = p.hardSets, progress = delta)
            }

        // --- MEV / MAV / PV: quadratic dose-response fit when data supports it ---
        val quadFit = DoseResponseQuadraticFit.fit(dosePoints)
        var observedMev: Double
        var observedMav: Double
        var observedPv: Double
        var mevInterval: LandmarkConfidenceInterval
        var mavInterval: LandmarkConfidenceInterval
        var pvInterval: LandmarkConfidenceInterval
        if (quadFit != null) {
            observedMev = quadFit.mevRoot
            observedMav = quadFit.mavVertex
            observedPv = quadFit.pvPareto
            mevInterval = quadFit.mevInterval()
            mavInterval = quadFit.mavInterval()
            pvInterval = quadFit.pvInterval()
        } else {
            observedMev = estimateMev(anchoredPoints)
            observedMav = prior.mavMid
            observedPv = prior.pvMid
            mevInterval = LandmarkConfidenceInterval.Unavailable
            mavInterval = LandmarkConfidenceInterval.Unavailable
            pvInterval = LandmarkConfidenceInterval.Unavailable
        }

        // --- MRV: Banister fitness-fatigue fit when data supports it, else heuristic ---
        val banisterFit = BanisterFitnessFatigueFit.fit(dosePoints)
        var observedMrv: Double
        var mrvInterval: LandmarkConfidenceInterval
        if (banisterFit != null) {
            observedMrv = banisterFit.mrv
            mrvInterval = banisterFit.mrvInterval()
        } else {
            observedMrv =
                estimateMrv(
                    anchoredPoints = anchoredPoints,
                    volumePoints = volumePoints,
                    volumeResponse = input.volumeResponse,
                    prior = prior,
                )
            mrvInterval = LandmarkConfidenceInterval.Unavailable
        }

        var confidence =
            baseConfidence(
                weekCount = anchoredPoints.size,
                volumeSpread = volumeSpread,
                rirCoverage = input.rirCoverage,
                volumeResponse = input.volumeResponse,
            )

        if (!isPlausible(observedMev, prior.mevLow, prior.mevHigh)) {
            confidence *= VolumeLandmarkEstimatorConstants.IMPLAUSIBLE_CONFIDENCE_PENALTY
            observedMev = prior.mevMid
            mevInterval = LandmarkConfidenceInterval.Unavailable
        }
        if (!isPlausible(observedMav, prior.mevHigh, prior.mrvLow)) {
            confidence *= VolumeLandmarkEstimatorConstants.IMPLAUSIBLE_CONFIDENCE_PENALTY
            observedMav = prior.mavMid
            mavInterval = LandmarkConfidenceInterval.Unavailable
        }
        if (!isPlausible(observedPv, prior.mevLow, prior.mavMid + 1.0)) {
            confidence *= VolumeLandmarkEstimatorConstants.IMPLAUSIBLE_CONFIDENCE_PENALTY
            observedPv = prior.pvMid
            pvInterval = LandmarkConfidenceInterval.Unavailable
        }
        if (!isPlausible(observedMrv, prior.mrvLow, prior.mrvHigh)) {
            confidence *= VolumeLandmarkEstimatorConstants.IMPLAUSIBLE_CONFIDENCE_PENALTY
            observedMrv = prior.mrvMid
            mrvInterval = LandmarkConfidenceInterval.Unavailable
        }

        val weight = confidence.coerceIn(0.0, 1.0)
        val finalMev = (weight * observedMev) + ((1.0 - weight) * prior.mevMid)
        val finalMav = (weight * observedMav) + ((1.0 - weight) * prior.mavMid)
        val finalPv = (weight * observedPv) + ((1.0 - weight) * prior.pvMid)
        val finalMrv = (weight * observedMrv) + ((1.0 - weight) * prior.mrvMid)

        // Volume is counted in hard sets; fractional blends with the prior would produce values
        // like 6.3 sets. Round up (ceil) so the reported MEV/MRV are whole-set targets the user
        // can actually program — a 6.3-set MEV needs 7 sets to be hit, not 6. MAV/PV likewise.
        val roundedMev = kotlin.math.ceil(finalMev.coerceAtLeast(0.0))
        val roundedMav = kotlin.math.ceil(finalMav).coerceAtLeast(roundedMev)
        val roundedPv = kotlin.math.ceil(finalPv).coerceAtLeast(roundedMev).coerceAtMost(roundedMav)
        val roundedMrv = kotlin.math.ceil(finalMrv).coerceAtLeast(roundedMav)

        // Pragmatic intervals fill in for any landmark that didn't get a statistical bound.
        val tier = confidenceToTier(confidence)
        val finalMevInterval = mevInterval.takeIf { it.isAvailable } ?: pragmaticInterval(roundedMev, prior, tier)
        val finalMavInterval = mavInterval.takeIf { it.isAvailable } ?: pragmaticInterval(roundedMav, prior, tier)
        val finalPvInterval = pvInterval.takeIf { it.isAvailable } ?: pragmaticInterval(roundedPv, prior, tier)
        val finalMrvInterval = mrvInterval.takeIf { it.isAvailable } ?: pragmaticInterval(roundedMrv, prior, tier)

        return EstimatedVolumeLandmark(
            muscle = input.muscle,
            estimatedMev = roundedMev,
            estimatedMav = roundedMav,
            estimatedMrv = roundedMrv,
            estimatedPv = roundedPv,
            mevInterval = finalMevInterval,
            mavInterval = finalMavInterval,
            mrvInterval = finalMrvInterval,
            pvInterval = finalPvInterval,
            confidenceTier = tier,
        )
    }

    private fun fallback(
        prior: VolumeLandmarkPrior,
        muscle: MuscleGroup,
        tier: LandmarkConfidenceTier,
    ): EstimatedVolumeLandmark {
        val mev = kotlin.math.ceil(prior.mevMid)
        val mav = kotlin.math.ceil(prior.mavMid).coerceAtLeast(mev)
        val pv = kotlin.math.ceil(prior.pvMid).coerceAtLeast(mev).coerceAtMost(mav)
        val mrv = kotlin.math.ceil(prior.mrvMid).coerceAtLeast(mav)
        return EstimatedVolumeLandmark(
            muscle = muscle,
            estimatedMev = mev,
            estimatedMav = mav,
            estimatedMrv = mrv,
            estimatedPv = pv,
            mevInterval = LandmarkConfidenceInterval.Unavailable,
            mavInterval = LandmarkConfidenceInterval.Unavailable,
            mrvInterval = LandmarkConfidenceInterval.Unavailable,
            pvInterval = LandmarkConfidenceInterval.Unavailable,
            confidenceTier = tier,
        )
    }

    /**
     * Approximate confidence interval derived from the estimator confidence tier and the prior
     * band width. Used when no statistical fit was possible. The band widens as confidence
     * drops: High ±15% of the prior half-band, Medium ±35%, Low ±60%. Kept on the pragmatic side
     * (the true fit would be tighter or wider depending on residual variance) but gives the UI a
     * meaningful spread instead of a blank.
     */
    private fun pragmaticInterval(
        point: Double,
        prior: VolumeLandmarkPrior,
        tier: LandmarkConfidenceTier,
    ): LandmarkConfidenceInterval {
        val halfBand = ((prior.mrvHigh - prior.mevLow) / 2.0).coerceAtLeast(1.0)
        val spread = when (tier) {
            LandmarkConfidenceTier.High -> halfBand * VolumeLandmarkEstimatorConstants.PRAGMATIC_INTERVAL_SPREAD_HIGH
            LandmarkConfidenceTier.Medium -> halfBand * VolumeLandmarkEstimatorConstants.PRAGMATIC_INTERVAL_SPREAD_MEDIUM
            LandmarkConfidenceTier.Low -> halfBand * VolumeLandmarkEstimatorConstants.PRAGMATIC_INTERVAL_SPREAD_LOW
        }
        return LandmarkConfidenceInterval(
            low = (point - spread).coerceAtLeast(0.0),
            high = point + spread,
            method = IntervalMethod.Pragmatic,
        )
    }

    private fun estimateMev(points: List<MuscleWeeklyVolumePoint>): Double {
        val sortedByVolume = points.sortedBy { it.hardSets }
        for (candidate in sortedByVolume) {
            val delta = candidate.weekOverWeekE1rmDelta
            if (delta == null || delta >= -VolumeLandmarkEstimatorConstants.STAGNATION_DELTA_LBS) {
                return candidate.hardSets
            }
        }
        return sortedByVolume.first().hardSets
    }

    private fun estimateMrv(
        anchoredPoints: List<MuscleWeeklyVolumePoint>,
        volumePoints: List<MuscleWeeklyVolumePoint>,
        volumeResponse: VolumeResponseObservation?,
        prior: VolumeLandmarkPrior,
    ): Double {
        val volumeSource = volumePoints.ifEmpty { anchoredPoints }
        val anchorByWeek = anchoredPoints.associateBy { it.weekStart }

        volumeResponse?.let { response ->
            if (response.correlationSlope > 0) {
                val observedMax =
                    maxOf(
                        response.observedWeeklySetRange.max,
                        volumeSource.maxOfOrNull { it.hardSets } ?: 0.0,
                    )
                val highestVolumeWeek = volumeSource.maxByOrNull { it.hardSets }
                val anchorAtCeiling =
                    highestVolumeWeek?.let { anchorByWeek[it.weekStart] }
                val stillProgressingAtCeiling =
                    highestVolumeWeek != null &&
                        highestVolumeWeek.hardSets >= observedMax - VolumeLandmarkEstimatorConstants.VOLUME_EPSILON &&
                        (
                            anchorAtCeiling == null ||
                                (anchorAtCeiling.weekOverWeekE1rmDelta ?: 0.0) >
                                VolumeLandmarkEstimatorConstants.STAGNATION_DELTA_LBS
                            )
                if (stillProgressingAtCeiling) {
                    val blend = VolumeLandmarkEstimatorConstants.CEILING_BLEND_TOWARD_PRIOR
                    return (blend * prior.mrvMid) + ((1.0 - blend) * observedMax)
                }
                return observedMax
            }
            if (response.correlationSlope <= 0) {
                val plateauVolume =
                    anchoredPoints
                        .filter {
                            (it.weekOverWeekE1rmDelta ?: 0.0) <
                                -VolumeLandmarkEstimatorConstants.STAGNATION_DELTA_LBS
                        }
                        .minOfOrNull { it.hardSets }
                if (plateauVolume != null) {
                    return (
                        plateauVolume - VolumeLandmarkEstimatorConstants.PLATEAU_VOLUME_STEP_DOWN
                        ).coerceAtLeast(VolumeLandmarkEstimatorConstants.MIN_RECOVERABLE_WEEKLY_SETS.toDouble())
                }
            }
        }

        val peakVolume = volumeSource.maxOf { it.hardSets }
        val stagnatedWeek =
            volumeSource
                .sortedByDescending { it.hardSets }
                .firstOrNull { candidate -> isStagnatedAnchorWeek(candidate, anchorByWeek) }
        return stagnatedWeek?.let { week ->
            (
                week.hardSets - VolumeLandmarkEstimatorConstants.PLATEAU_VOLUME_STEP_DOWN
                ).coerceAtLeast(VolumeLandmarkEstimatorConstants.MIN_RECOVERABLE_WEEKLY_SETS.toDouble())
        } ?: peakVolume
    }

    private fun isStagnatedAnchorWeek(
        candidate: MuscleWeeklyVolumePoint,
        anchorByWeek: Map<Instant, MuscleWeeklyVolumePoint>,
    ): Boolean {
        val anchor = anchorByWeek[candidate.weekStart] ?: return false
        return (anchor.weekOverWeekE1rmDelta ?: 0.0) <
            -VolumeLandmarkEstimatorConstants.STAGNATION_DELTA_LBS
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
