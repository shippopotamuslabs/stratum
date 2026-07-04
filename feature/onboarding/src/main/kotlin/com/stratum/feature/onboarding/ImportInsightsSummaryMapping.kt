package com.stratum.feature.onboarding

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.importing.EffectiveLiftHighlight
import com.stratum.domain.importing.HistoricalImportSummary
import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.MuscleVolumeHighlight
import com.stratum.domain.importing.VolumeResponseHighlight
import com.stratum.domain.response.modeling.VolumePosture
import java.util.Locale
import kotlin.math.roundToInt

internal fun HistoricalImportSummary.toImportInsightsSummaryUi(): ImportInsightsSummaryUi =
    ImportInsightsSummaryUi(
        totalSessions = totalSessions,
        totalSets = totalSets,
        analysisWeeks = analysisWeeks,
        enabledLiftCount = enabledLiftCount,
        provenLiftCount = provenLiftCount,
        customLiftsCreated = customLiftsCreated,
        liftsWithInsights = liftsWithInsights,
        supersetPairs = supersetPairs,
        volumeSnapshots = volumeHighlights.map { it.toUi() },
        effectiveLifts = effectiveLifts.map { it.toUi() },
        volumeResponses = volumeResponses.map { it.toUi() },
    )

private fun MuscleVolumeHighlight.toUi(): MuscleVolumeSnapshotUi =
    MuscleVolumeSnapshotUi(
        muscleLabel = muscle.displayLabel,
        mvWeeklyHardSets = retentionFloorMv.formatWeeklySets(),
        pvWeeklyHardSets = paretoVolumePv.formatWeeklySets(),
        rcWeeklyHardSets = recoverabilityCeilingRc.formatWeeklySets(),
        isSaturatedPosture =
            posture == VolumePosture.Saturated ||
                (
                    observedDoseMax >= GlobalVolumePriors.RECOVERABILITY_CEILING_RC * 0.9 &&
                        observedDoseMedian >= GlobalVolumePriors.PARETO_VOLUME_PV * 0.85
                    ),
        personalizedFromLogs = personalizedFromLogs,
        observedMinWeeklyHardSets = observedDoseMin.formatWeeklySets(),
        observedMedianWeeklyHardSets = observedDoseMedian,
        observedMedianWeeklyHardSetsLabel = observedDoseMedian.formatWeeklySets(),
        observedMaxWeeklyHardSets = observedDoseMax.formatWeeklySets(),
        pvIntervalLabel = pvInterval.formatInterval(),
        confidenceTier = confidenceTier,
    )

private fun EffectiveLiftHighlight.toUi(): EffectiveLiftHighlightUi =
    EffectiveLiftHighlightUi(
        muscleLabel = muscle.displayLabel,
        liftName = liftName,
        effectivenessTier = effectivenessTier,
        confidenceTier = confidenceTier,
    )

private fun VolumeResponseHighlight.toUi(): VolumeResponseHighlightUi =
    VolumeResponseHighlightUi(
        muscleLabel = muscle.displayLabel,
        evidence = evidence,
        respondsToVolume = respondsToVolume,
    )

private fun Double.formatWeeklySets(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded == rounded.roundToInt().toDouble()) {
        rounded.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

private fun LandmarkConfidenceInterval.formatInterval(): String {
    if (!isAvailable) return ""
    val lowRounded = kotlin.math.round(low).toInt()
    val highRounded = kotlin.math.round(high).toInt()
    if (lowRounded <= 0 && highRounded <= 0) return ""
    if (lowRounded == highRounded) return lowRounded.toString()
    return "$lowRounded–$highRounded"
}
