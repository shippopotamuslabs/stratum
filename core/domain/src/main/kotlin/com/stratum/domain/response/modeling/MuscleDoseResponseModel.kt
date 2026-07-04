@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.importing.IntervalMethod
import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.ResolvedGlobalVolumePriors
import com.stratum.domain.importing.VolumeDosePoint
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.response.AnchorLiftSelector
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.ResponseExtractionInput
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object MuscleDoseResponseModel {
    fun estimate(
        muscle: MuscleGroup,
        pairing: LaggedDoseResponsePairer.PairingResult,
        indices: List<MuscleWeeklyOutcomeIndex>,
        stimulus: List<MuscleWeeklyStimulus>,
        diagnosticAnchorLiftId: LiftId?,
        trainingAge: TrainingAge? = null,
        globals: ResolvedGlobalVolumePriors = GlobalVolumePriors.forTrainingAge(trainingAge),
    ): MuscleResponseModelResult {
        val points = pairing.points
        val quality = assessModelQuality(points, indices, stimulus)
        val outcomeModelType = resolveOutcomeModelType(quality.rejectionReasons, diagnosticAnchorLiftId)
        val dosePoints = points.map { VolumeDosePoint(volume = it.dose, progress = it.response) }
        val sqrtFit = SqrtDoseResponseFit.fit(dosePoints)
        val observed = observedDoseRange(points, stimulus, quality.peakDose)
        val productiveBand = productiveDoseBand(points)
        val posture = classifyPosture(points, sqrtFit, quality.rejectionReasons)
        val confidenceScore =
            ResponseModelDiagnostics.confidenceScore(
                indexWeekCount = quality.indexWeekCount,
                pairedPointCount = points.size,
                volumeBinCount = quality.volumeBins,
                peakDoseCoverage = quality.peakCoverage,
                compositionStability = quality.compositionStability,
                predictiveScore = pairing.predictiveScore,
                residualError = sqrtFit?.residualError,
                rejectionReasons = quality.rejectionReasons,
            )
        val weight = modelWeight(quality.rejectionReasons, confidenceScore)
        val confidenceTier = ResponseModelDiagnostics.confidenceTier(weight)
        val personalizationWeight =
            resolvePersonalizationWeight(
                modelWeight = weight,
                stimulusMax = observed.stimulusMax,
                loggedWeekCount = max(quality.indexWeekCount, stimulus.size),
                globals = globals,
            )
        val resolved =
            resolveVolumes(
                globals = globals,
                productiveBand = productiveBand,
                observedMedian = observed.median,
                observedMin = observed.min,
                observedMax = observed.max,
                posture = posture,
                personalWeight = personalizationWeight,
            )
        val pvInterval =
            resolvePvInterval(
                dosePoints = dosePoints,
                points = points,
                paretoVolumePv = resolved.paretoVolumePv,
                confidenceTier = confidenceTier,
            )
        val snapshot =
            MuscleVolumeResponseSnapshot(
                muscle = muscle,
                retentionFloorMv = resolved.retentionFloorMv,
                paretoVolumePv = resolved.paretoVolumePv,
                recoverabilityCeilingRc = resolved.recoverabilityCeilingRc,
                growthEntryFloor = resolved.growthEntryFloor,
                observedDoseMin = observed.min.coerceAtLeast(0.0),
                observedDoseMax = observed.max.coerceAtLeast(0.0),
                observedDoseMedian = observed.median,
                productiveBandMin = productiveBand?.first,
                productiveBandMax = productiveBand?.second,
                posture = posture,
                pvInterval = pvInterval,
                confidenceTier = confidenceTier,
                outcomeModelType = outcomeModelType,
                diagnosticAnchorLiftId = diagnosticAnchorLiftId,
            )
        val diagnostics =
            MuscleResponseModelDiagnostic(
                muscle = muscle,
                selectedLagWeeks = pairing.selectedLagWeeks,
                responseWindowWeeks = pairing.responseWindowWeeks,
                pairedPointCount = points.size,
                volumeBinCount = quality.volumeBins,
                indexWeekCount = quality.indexWeekCount,
                peakDoseCoverage = quality.peakCoverage.coerceIn(0.0, 1.0),
                compositionStability = quality.compositionStability.coerceIn(0.0, 1.0),
                residualError = sqrtFit?.residualError,
                predictiveScore = pairing.predictiveScore?.takeIf { it.isFinite() },
                anchorAgreement = null,
                rejectionReasons = quality.rejectionReasons,
                confidenceScore = weight,
            )
        return MuscleResponseModelResult(snapshot = snapshot, diagnostics = diagnostics, dosePoints = points)
    }

    fun diagnosticAnchor(
        muscle: MuscleGroup,
        epochs: List<Epoch>,
        input: ResponseExtractionInput,
    ): LiftId? = AnchorLiftSelector.select(muscle, epochs, input)

    private data class ModelQualityContext(
        val indexWeekCount: Int,
        val volumeBins: Int,
        val peakDose: Double,
        val peakCoverage: Double,
        val compositionStability: Double,
        val rejectionReasons: List<String>,
    )

    private data class ObservedDoseRange(
        val min: Double,
        val max: Double,
        val median: Double,
        val stimulusMax: Double,
    )

    private data class ResolvedVolumes(
        val retentionFloorMv: Double,
        val paretoVolumePv: Double,
        val recoverabilityCeilingRc: Double,
        val growthEntryFloor: Double,
    )

    private fun assessModelQuality(
        points: List<MuscleDoseResponsePoint>,
        indices: List<MuscleWeeklyOutcomeIndex>,
        stimulus: List<MuscleWeeklyStimulus>,
    ): ModelQualityContext {
        val indexWeekCount = indices.size
        val volumeBins = points.map { it.dose }.distinct().size
        val peakDose = stimulus.maxOfOrNull { it.hardSets } ?: 0.0
        val peakPairedDose = points.maxOfOrNull { it.dose } ?: 0.0
        val peakCoverage = if (peakDose <= 0.0) 0.0 else peakPairedDose / peakDose
        val compositionStability = compositionStability(indices)
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
        return ModelQualityContext(
            indexWeekCount = indexWeekCount,
            volumeBins = volumeBins,
            peakDose = peakDose,
            peakCoverage = peakCoverage,
            compositionStability = compositionStability,
            rejectionReasons = rejectionReasons,
        )
    }

    private fun compositionStability(indices: List<MuscleWeeklyOutcomeIndex>): Double =
        if (indices.isEmpty()) {
            0.0
        } else {
            1.0 - indices.map { it.compositionDrift }.average().coerceIn(0.0, 1.0)
        }

    private fun resolveOutcomeModelType(
        rejectionReasons: List<String>,
        diagnosticAnchorLiftId: LiftId?,
    ): OutcomeModelType =
        when {
            rejectionReasons.isNotEmpty() && diagnosticAnchorLiftId != null -> OutcomeModelType.BestAnchor
            rejectionReasons.isNotEmpty() -> OutcomeModelType.PriorOnly
            else -> OutcomeModelType.MuscleIndex
        }

    private fun observedDoseRange(
        points: List<MuscleDoseResponsePoint>,
        stimulus: List<MuscleWeeklyStimulus>,
        peakDose: Double,
    ): ObservedDoseRange {
        val stimulusHardSets = stimulus.map { it.hardSets }.filter { it > 0.0 }
        val stimulusMin = stimulusHardSets.minOrNull() ?: 0.0
        val stimulusMax = stimulusHardSets.maxOrNull() ?: 0.0
        val stimulusMedian = medianValues(stimulusHardSets)
        val pairedMin = points.minOfOrNull { it.dose } ?: 0.0
        val pairedMax = points.maxOfOrNull { it.dose } ?: 0.0
        val observedMin =
            when {
                stimulusMin > 0.0 && pairedMin > 0.0 -> min(stimulusMin, pairedMin)
                stimulusMin > 0.0 -> stimulusMin
                else -> pairedMin
            }
        val observedMax = max(max(pairedMax, stimulusMax), peakDose)
        val observedMedian =
            max(
                if (points.isNotEmpty()) medianDose(points) else 0.0,
                stimulusMedian,
            )
        return ObservedDoseRange(
            min = observedMin,
            max = observedMax,
            median = observedMedian,
            stimulusMax = stimulusMax,
        )
    }

    private fun modelWeight(rejectionReasons: List<String>, confidenceScore: Double): Double =
        if (rejectionReasons.isNotEmpty()) {
            (confidenceScore * 0.55).coerceIn(0.0, 1.0)
        } else {
            confidenceScore.coerceIn(0.0, 1.0)
        }

    private fun resolvePersonalizationWeight(
        modelWeight: Double,
        stimulusMax: Double,
        loggedWeekCount: Int,
        globals: ResolvedGlobalVolumePriors,
    ): Double {
        val floorWeight =
            when {
                stimulusMax > globals.recoverabilityCeilingRc &&
                    loggedWeekCount >= ResponseModelingConstants.MIN_INDEX_WEEKS_FOR_MODEL -> 0.38
                stimulusMax > globals.paretoVolumePv && loggedWeekCount >= 8 -> 0.28
                else -> 0.0
            }
        return max(modelWeight, floorWeight)
    }

    private fun resolvePvInterval(
        dosePoints: List<VolumeDosePoint>,
        points: List<MuscleDoseResponsePoint>,
        paretoVolumePv: Double,
        confidenceTier: LandmarkConfidenceTier,
    ): LandmarkConfidenceInterval {
        val epochIds = epochIdsForPoints(points)
        val pvBand =
            EpochBlockedBootstrap.predictionBand(
                points = dosePoints,
                epochIds = epochIds,
                referenceDose = paretoVolumePv,
            )
        return if (pvBand != null) {
            LandmarkConfidenceInterval(
                low = pvBand.low,
                high = pvBand.high,
                method = IntervalMethod.EpochBlockedBootstrap,
            )
        } else {
            pragmaticPvInterval(paretoVolumePv, confidenceTier)
        }
    }

    private fun resolveVolumes(
        globals: ResolvedGlobalVolumePriors,
        productiveBand: Pair<Double, Double>?,
        observedMedian: Double,
        observedMin: Double,
        observedMax: Double,
        posture: VolumePosture,
        personalWeight: Double,
    ): ResolvedVolumes {
        var pv = globals.paretoVolumePv
        var rc = globals.recoverabilityCeilingRc
        var entry = globals.growthEntryFloor
        val weight = personalWeight.coerceIn(0.0, 1.0)
        val hasMeaningfulObservations = observedMax > globals.retentionFloorMv

        // Observed-range personalization applies even when posture is Unclear — the common case on real logs.
        if (hasMeaningfulObservations && weight >= 0.25) {
            if (observedMax > rc) {
                val excessRatio = observedMax / globals.recoverabilityCeilingRc
                val rcBlend =
                    (weight + ((excessRatio - 1.0) * 0.35))
                        .coerceIn(0.25, 0.85)
                rc = blend(rc, observedMax, rcBlend)
            }
            val personalPvFromRc = GlobalVolumePriors.paretoVolumeFromCeiling(rc)
            pv = blend(pv, personalPvFromRc, weight * 0.55)
            if (observedMedian > globals.retentionFloorMv) {
                pv = blend(pv, observedMedian, weight * 0.4)
            }
        }

        if (weight >= ResponseModelingConstants.MEDIUM_CONFIDENCE_THRESHOLD && productiveBand != null) {
            val anchor = (productiveBand.first + productiveBand.second) / 2.0
            pv = blend(pv, anchor, weight * 0.5)
            when (posture) {
                VolumePosture.RoomToGrow -> {
                    entry = blend(entry, productiveBand.first, weight * 0.5)
                        .coerceAtLeast(globals.retentionFloorMv)
                }
                VolumePosture.Saturated -> {
                    rc = min(rc, observedMax + 2.0)
                    pv = min(pv, observedMedian + 1.0)
                }
                VolumePosture.Holding -> {
                    pv = blend(pv, observedMedian, weight * 0.5)
                }
                VolumePosture.Unclear -> Unit
            }
        }

        if (posture == VolumePosture.RoomToGrow && observedMin < entry && weight >= 0.35) {
            entry = max(globals.retentionFloorMv, blend(entry, observedMin, weight * 0.4))
        }

        return ResolvedVolumes(
            retentionFloorMv = roundHalf(globals.retentionFloorMv),
            paretoVolumePv = roundHalf(pv.coerceIn(globals.retentionFloorMv, rc)),
            recoverabilityCeilingRc = roundHalf(rc),
            growthEntryFloor = roundHalf(entry.coerceIn(globals.retentionFloorMv, rc)),
        )
    }

    private fun classifyPosture(
        points: List<MuscleDoseResponsePoint>,
        fit: SqrtDoseResponseFit?,
        rejectionReasons: List<String>,
    ): VolumePosture {
        if (rejectionReasons.isNotEmpty()) return VolumePosture.Unclear
        if (fit == null || points.size < ResponseModelingConstants.MIN_PAIRED_POINTS_FOR_MODEL) {
            return VolumePosture.Unclear
        }
        val medianDose = medianDose(points)
        val upperDose = points.map { it.dose }.sorted().let { sorted ->
            sorted[(sorted.size * 0.75).toInt().coerceIn(0, sorted.lastIndex)]
        }
        val marginalMedian = fit.marginalSlopeAt(medianDose)
        val marginalUpper = fit.marginalSlopeAt(upperDose)
        val upperResponses = points.filter { it.dose >= upperDose * 0.9 }.map { it.response }
        val upperFlat =
            upperResponses.size >= 2 &&
                upperResponses.average() < ResponseModelingConstants.PROGRESS_THRESHOLD_LOG * 2

        return when {
            marginalUpper < marginalMedian * 0.35 || upperFlat -> VolumePosture.Saturated
            marginalMedian > ResponseModelingConstants.PROGRESS_THRESHOLD_LOG -> VolumePosture.RoomToGrow
            marginalMedian > 0.0 -> VolumePosture.Holding
            else -> VolumePosture.Unclear
        }
    }

    private fun productiveDoseBand(points: List<MuscleDoseResponsePoint>): Pair<Double, Double>? {
        if (points.size < 4) return null
        val threshold = points.map { it.response }.sorted().let { sorted ->
            sorted[(sorted.size * 0.75).toInt().coerceIn(0, sorted.lastIndex)]
        }
        val productive = points.filter { it.response >= threshold }
        if (productive.size < 2) return null
        val doses = productive.map { it.dose }
        return doses.min() to doses.max()
    }

    private fun medianDose(points: List<MuscleDoseResponsePoint>): Double = medianValues(points.map { it.dose })

    private fun medianValues(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    /** Four-week blocks as epoch units for blocked bootstrap when explicit epochs are unavailable. */
    private fun epochIdsForPoints(points: List<MuscleDoseResponsePoint>): List<Int> {
        val orderedWeeks = points.map { it.weekStart }.distinct().sorted()
        val weekToEpoch = orderedWeeks.mapIndexed { index, week -> week to index / 4 }.toMap()
        return points.map { weekToEpoch[it.weekStart] ?: 0 }
    }

    private fun blend(prior: Double, observed: Double, weight: Double): Double {
        val w = weight.coerceIn(0.0, 1.0)
        return (w * observed) + ((1.0 - w) * prior)
    }

    private fun roundHalf(value: Double): Double = round(value * 2) / 2.0

    private fun pragmaticPvInterval(
        pv: Double,
        tier: LandmarkConfidenceTier,
    ): LandmarkConfidenceInterval {
        val spread =
            when (tier) {
                LandmarkConfidenceTier.High -> 1.5
                LandmarkConfidenceTier.Medium -> 2.5
                LandmarkConfidenceTier.Low -> 4.0
            }
        return LandmarkConfidenceInterval(
            low = (pv - spread).coerceAtLeast(GlobalVolumePriors.RETENTION_FLOOR_MV),
            high = pv + spread,
            method = IntervalMethod.Pragmatic,
        )
    }
}
