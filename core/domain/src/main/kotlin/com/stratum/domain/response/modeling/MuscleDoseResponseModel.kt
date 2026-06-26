@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.importing.DoseResponseQuadraticFit
import com.stratum.domain.importing.IntervalMethod
import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.VolumeDosePoint
import com.stratum.domain.importing.VolumeLandmarkEstimatorConstants
import com.stratum.domain.importing.VolumeLandmarkPriors
import com.stratum.domain.importing.VolumeLandmarkPrior
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.AnchorLiftSelector
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.ResponseExtractionInput
import kotlin.math.ceil
import kotlin.math.max

object MuscleDoseResponseModel {
    fun estimate(
        muscle: MuscleGroup,
        pairing: LaggedDoseResponsePairer.PairingResult,
        indices: List<MuscleWeeklyOutcomeIndex>,
        stimulus: List<MuscleWeeklyStimulus>,
        diagnosticAnchorLiftId: LiftId?,
        prior: VolumeLandmarkPrior = VolumeLandmarkPriors.forMuscle(muscle),
    ): MuscleResponseModelResult {
        val points = pairing.points
        val indexWeekCount = indices.size
        val volumeBins = points.map { it.dose }.distinct().size
        val peakDose = stimulus.maxOfOrNull { it.hardSets } ?: 0.0
        val peakPairedDose = points.maxOfOrNull { it.dose } ?: 0.0
        val peakCoverage = if (peakDose <= 0.0) 0.0 else peakPairedDose / peakDose
        val compositionStability =
            1.0 - indices.map { it.compositionDrift }.average().coerceIn(0.0, 1.0)
        val rejectionReasons = mutableListOf<String>()

        if (indexWeekCount < ResponseModelingConstants.MIN_INDEX_WEEKS_FOR_MODEL) {
            rejectionReasons += "insufficient_index_weeks"
        }
        if (points.size < ResponseModelingConstants.MIN_PAIRED_POINTS_FOR_MODEL) {
            rejectionReasons += "insufficient_paired_points"
        }
        if (volumeBins < ResponseModelingConstants.MIN_VOLUME_BINS) {
            rejectionReasons += "insufficient_volume_bins"
        }
        if (peakCoverage < ResponseModelingConstants.PEAK_DOSE_COVERAGE_MIN) {
            rejectionReasons += "low_peak_dose_coverage"
        }
        if (compositionStability < (1.0 - ResponseModelingConstants.COMPOSITION_DRIFT_PENALTY_THRESHOLD)) {
            rejectionReasons += "unstable_composition"
        }

        val outcomeModelType =
            when {
                rejectionReasons.isNotEmpty() && diagnosticAnchorLiftId != null -> OutcomeModelType.BestAnchor
                rejectionReasons.isNotEmpty() -> OutcomeModelType.PriorOnly
                else -> OutcomeModelType.MuscleIndex
            }

        val dosePoints = points.map { VolumeDosePoint(volume = it.dose, progress = it.response) }
        val monotonic = MonotonicDoseResponseFit.fit(dosePoints)
        val quadratic = DoseResponseQuadraticFit.fit(dosePoints)
        val useQuadratic = quadratic != null && (monotonic?.rSquared ?: 0.0) + 0.05 < (quadratic.rSquared)

        var observedMev: Double
        var observedMav: Double
        var observedPv: Double
        var observedMrv: Double
        var mevInterval = LandmarkConfidenceInterval.Unavailable
        var mavInterval = LandmarkConfidenceInterval.Unavailable
        var pvInterval = LandmarkConfidenceInterval.Unavailable
        var mrvInterval = LandmarkConfidenceInterval.Unavailable
        var mrvSource = MrvSource.Observed

        if (useQuadratic && quadratic != null) {
            observedMev = quadratic.mevRoot
            observedMav = quadratic.mavVertex
            observedPv = quadratic.pvPareto
            mevInterval = quadratic.mevInterval()
            mavInterval = quadratic.mavInterval()
            pvInterval = quadratic.pvInterval()
            observedMrv = estimateMrvFromPoints(points, prior, positiveSlope = quadratic.b > 0)
        } else if (monotonic != null) {
            observedMev = monotonic.mev
            observedMav = monotonic.mav
            observedPv = monotonic.pv
            observedMrv = monotonic.mrv
            mevInterval = pragmaticInterval(observedMev, prior, LandmarkConfidenceTier.Medium)
            mavInterval = pragmaticInterval(observedMav, prior, LandmarkConfidenceTier.Medium)
            pvInterval = pragmaticInterval(observedPv, prior, LandmarkConfidenceTier.Medium)
            mrvInterval = pragmaticInterval(observedMrv, prior, LandmarkConfidenceTier.Medium)
        } else {
            observedMev = prior.mevMid
            observedMav = prior.mavMid
            observedPv = prior.pvMid
            observedMrv = prior.mrvMid
            mrvSource = MrvSource.PriorDominant
        }

        if (observedMrv < peakDose - ResponseModelingConstants.VOLUME_EPSILON &&
            points.none { it.dose >= peakDose * 0.85 && it.response < -ResponseModelingConstants.PROGRESS_THRESHOLD_LOG }
        ) {
            observedMrv = max(observedMrv, peakDose)
            mrvSource = MrvSource.BoundedExtrapolation
        }

        val confidenceScore =
            ResponseModelDiagnostics.confidenceScore(
                indexWeekCount = indexWeekCount,
                pairedPointCount = points.size,
                volumeBinCount = volumeBins,
                peakDoseCoverage = peakCoverage,
                compositionStability = compositionStability,
                predictiveScore = pairing.predictiveScore,
                residualError = monotonic?.residualError ?: quadratic?.residualVariance,
                rejectionReasons = rejectionReasons,
            )
        val confidenceTier = ResponseModelDiagnostics.confidenceTier(confidenceScore)
        val weight = confidenceScore.coerceIn(0.0, 1.0)
        if (rejectionReasons.isNotEmpty()) {
            val penalty = 0.55
            val adjusted = (weight * penalty).coerceIn(0.0, 1.0)
            return buildResult(
                LandmarkBuildContext(
                    muscle = muscle,
                    observedMev = blend(observedMev, prior.mevMid, adjusted),
                    observedMav = blend(observedMav, prior.mavMid, adjusted),
                    observedPv = blend(observedPv, prior.pvMid, adjusted),
                    observedMrv = blend(observedMrv, prior.mrvMid, adjusted),
                    mevInterval = mevInterval,
                    mavInterval = mavInterval,
                    pvInterval = pvInterval,
                    mrvInterval = mrvInterval,
                    confidenceTier = ResponseModelDiagnostics.confidenceTier(adjusted),
                    outcomeModelType = outcomeModelType,
                    mrvSource = if (adjusted < 0.35) MrvSource.PriorDominant else mrvSource,
                    observedDoseMin = points.minOfOrNull { it.dose } ?: 0.0,
                    observedDoseMax = points.maxOfOrNull { it.dose } ?: peakDose,
                    diagnosticAnchorLiftId = diagnosticAnchorLiftId,
                    pairing = pairing,
                    indexWeekCount = indexWeekCount,
                    volumeBins = volumeBins,
                    peakCoverage = peakCoverage,
                    compositionStability = compositionStability,
                    rejectionReasons = rejectionReasons,
                    confidenceScore = adjusted,
                    points = points,
                ),
            )
        }

        return buildResult(
            LandmarkBuildContext(
                muscle = muscle,
                observedMev = blend(observedMev, prior.mevMid, weight),
                observedMav = blend(observedMav, prior.mavMid, weight),
                observedPv = blend(observedPv, prior.pvMid, weight),
                observedMrv = blend(observedMrv, prior.mrvMid, weight),
                mevInterval = mevInterval,
                mavInterval = mavInterval,
                pvInterval = pvInterval,
                mrvInterval = mrvInterval,
                confidenceTier = confidenceTier,
                outcomeModelType = outcomeModelType,
                mrvSource = mrvSource,
                observedDoseMin = points.minOfOrNull { it.dose } ?: 0.0,
                observedDoseMax = points.maxOfOrNull { it.dose } ?: peakDose,
                diagnosticAnchorLiftId = diagnosticAnchorLiftId,
                pairing = pairing,
                indexWeekCount = indexWeekCount,
                volumeBins = volumeBins,
                peakCoverage = peakCoverage,
                compositionStability = compositionStability,
                rejectionReasons = rejectionReasons,
                confidenceScore = weight,
                points = points,
            ),
        )
    }

    private data class LandmarkBuildContext(
        val muscle: MuscleGroup,
        val observedMev: Double,
        val observedMav: Double,
        val observedPv: Double,
        val observedMrv: Double,
        val mevInterval: LandmarkConfidenceInterval,
        val mavInterval: LandmarkConfidenceInterval,
        val pvInterval: LandmarkConfidenceInterval,
        val mrvInterval: LandmarkConfidenceInterval,
        val confidenceTier: LandmarkConfidenceTier,
        val outcomeModelType: OutcomeModelType,
        val mrvSource: MrvSource,
        val observedDoseMin: Double,
        val observedDoseMax: Double,
        val diagnosticAnchorLiftId: LiftId?,
        val pairing: LaggedDoseResponsePairer.PairingResult,
        val indexWeekCount: Int,
        val volumeBins: Int,
        val peakCoverage: Double,
        val compositionStability: Double,
        val rejectionReasons: List<String>,
        val confidenceScore: Double,
        val points: List<MuscleDoseResponsePoint>,
    )

    fun diagnosticAnchor(
        muscle: MuscleGroup,
        epochs: List<Epoch>,
        input: ResponseExtractionInput,
    ): LiftId? = AnchorLiftSelector.select(muscle, epochs, input)

    private fun buildResult(context: LandmarkBuildContext): MuscleResponseModelResult {
        val roundedMev =
            ceil(context.observedMev.coerceAtLeast(ResponseModelingConstants.MIN_RECOVERABLE_WEEKLY_SETS.toDouble()))
        val roundedMav = ceil(context.observedMav).coerceAtLeast(roundedMev)
        val roundedPv = ceil(context.observedPv).coerceAtLeast(roundedMev).coerceAtMost(roundedMav)
        val roundedMrv = ceil(context.observedMrv).coerceAtLeast(roundedMav)
        val musclePrior = VolumeLandmarkPriors.forMuscle(context.muscle)
        val landmarks =
            MuscleVolumeLandmarkEstimate(
                muscle = context.muscle,
                estimatedMev = roundedMev,
                estimatedMav = roundedMav,
                estimatedMrv = roundedMrv,
                estimatedPv = roundedPv,
                mevInterval = context.mevInterval.takeIf { it.isAvailable }
                    ?: pragmaticInterval(roundedMev, musclePrior, context.confidenceTier),
                mavInterval = context.mavInterval.takeIf { it.isAvailable }
                    ?: pragmaticInterval(roundedMav, musclePrior, context.confidenceTier),
                mrvInterval = context.mrvInterval.takeIf { it.isAvailable }
                    ?: pragmaticInterval(roundedMrv, musclePrior, context.confidenceTier),
                pvInterval = context.pvInterval.takeIf { it.isAvailable }
                    ?: pragmaticInterval(roundedPv, musclePrior, context.confidenceTier),
                confidenceTier = context.confidenceTier,
                outcomeModelType = context.outcomeModelType,
                mrvSource = context.mrvSource,
                observedDoseMin = context.observedDoseMin,
                observedDoseMax = context.observedDoseMax,
                diagnosticAnchorLiftId = context.diagnosticAnchorLiftId,
            )
        val diagnostics =
            MuscleResponseModelDiagnostic(
                muscle = context.muscle,
                selectedLagWeeks = context.pairing.selectedLagWeeks,
                responseWindowWeeks = context.pairing.responseWindowWeeks,
                pairedPointCount = context.points.size,
                volumeBinCount = context.volumeBins,
                indexWeekCount = context.indexWeekCount,
                peakDoseCoverage = context.peakCoverage,
                compositionStability = context.compositionStability,
                residualError = null,
                predictiveScore = context.pairing.predictiveScore,
                anchorAgreement = null,
                rejectionReasons = context.rejectionReasons,
                confidenceScore = context.confidenceScore,
            )
        return MuscleResponseModelResult(landmarks = landmarks, diagnostics = diagnostics, dosePoints = context.points)
    }

    private fun blend(observed: Double, prior: Double, weight: Double): Double =
        (weight * observed) + ((1.0 - weight) * prior)

    private fun estimateMrvFromPoints(
        points: List<MuscleDoseResponsePoint>,
        prior: VolumeLandmarkPrior,
        positiveSlope: Boolean,
    ): Double {
        val peakDose = points.maxOfOrNull { it.dose } ?: prior.mrvMid
        if (positiveSlope) {
            return max(peakDose, prior.mevHigh)
        }
        val plateau =
            points.filter { it.response < -ResponseModelingConstants.PROGRESS_THRESHOLD_LOG }
                .minOfOrNull { it.dose }
        return plateau?.minus(ResponseModelingConstants.PLATEAU_VOLUME_STEP_DOWN)
            ?.coerceAtLeast(ResponseModelingConstants.MIN_RECOVERABLE_WEEKLY_SETS.toDouble())
            ?: peakDose
    }

    private fun pragmaticInterval(
        point: Double,
        prior: VolumeLandmarkPrior,
        tier: LandmarkConfidenceTier,
    ): LandmarkConfidenceInterval {
        val halfBand = ((prior.mrvHigh - prior.mevLow) / 2.0).coerceAtLeast(1.0)
        val spread =
            when (tier) {
                LandmarkConfidenceTier.High -> halfBand * VolumeLandmarkEstimatorConstants.PRAGMATIC_INTERVAL_SPREAD_HIGH
                LandmarkConfidenceTier.Medium -> halfBand * VolumeLandmarkEstimatorConstants.PRAGMATIC_INTERVAL_SPREAD_MEDIUM
                LandmarkConfidenceTier.Low -> halfBand * VolumeLandmarkEstimatorConstants.PRAGMATIC_INTERVAL_SPREAD_LOW
            }
        return LandmarkConfidenceInterval(
            low = (point - spread).coerceAtLeast(ResponseModelingConstants.MIN_RECOVERABLE_WEEKLY_SETS.toDouble()),
            high = point + spread,
            method = IntervalMethod.Pragmatic,
        )
    }
}
