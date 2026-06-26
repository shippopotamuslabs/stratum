package com.stratum.feature.onboarding

import com.stratum.domain.importing.EffectiveLiftHighlight
import com.stratum.domain.importing.HistoricalImportSummary
import com.stratum.domain.importing.MuscleVolumeHighlight
import com.stratum.domain.importing.VolumeResponseHighlight
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
        volumeLandmarks = volumeHighlights.map { it.toUi() },
        effectiveLifts = effectiveLifts.map { it.toUi() },
        volumeResponses = volumeResponses.map { it.toUi() },
    )

private fun MuscleVolumeHighlight.toUi(): MuscleVolumeLandmarkUi =
    MuscleVolumeLandmarkUi(
        muscleLabel = muscle.displayLabel,
        mevWeeklyHardSets = estimatedMev.formatWeeklySets(),
        mrvWeeklyHardSets = estimatedMrv.formatWeeklySets(),
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
