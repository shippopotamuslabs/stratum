package com.stratum.domain.response

import java.time.Instant
import kotlin.math.abs
import kotlin.math.sign

object OutlierGuard {
    fun winsorizeWeeklySignals(signals: List<WeeklyTrainingSignal>): List<WeeklyTrainingSignal> {
        if (signals.isEmpty()) return signals
        val sorted = signals.sortedBy { it.weekStart }
        val peaks = sorted.map { it.peakEstimatedOneRm ?: it.smoothedEstimatedOneRm ?: 0.0 }
        val median = peaks.sorted()[peaks.size / 2]
        val mad =
            peaks.map { abs(it - median) }
                .sorted()[peaks.size / 2]
                .coerceAtLeast(MAD_MIN_EPSILON)
        val threshold = MAD_OUTLIER_THRESHOLD * mad

        return sorted.mapIndexed { index, signal ->
            val peak = peaks[index]
            val isOutlier = abs(peak - median) > threshold
            val rollingMedian = rollingMedianPeaks(sorted, index)
            val smoothed = rollingMedian ?: peak
            signal.copy(
                peakEstimatedOneRm = signal.peakEstimatedOneRm ?: peak,
                smoothedEstimatedOneRm = if (isOutlier && rollingMedian != null) rollingMedian else smoothed,
                qualityFlags =
                    if (isOutlier) {
                        signal.qualityFlags + TrainingSignalQualityFlag.OutlierWinsorized
                    } else {
                        signal.qualityFlags
                    },
            )
        }
    }

    fun suspiciousJumpFlags(signals: List<WeeklyTrainingSignal>): Set<Instant> {
        val sorted = signals.sortedBy { it.weekStart }
        if (sorted.size < 2) return emptySet()
        val flags = mutableSetOf<Instant>()
        for (index in 1 until sorted.size) {
            val previous = sorted[index - 1].peakEstimatedOneRm ?: sorted[index - 1].smoothedEstimatedOneRm
            val current = sorted[index].peakEstimatedOneRm ?: sorted[index].smoothedEstimatedOneRm
            if (previous == null || current == null || previous <= 0.0) continue
            val jump = (current - previous) / previous
            if (jump > SUSPICIOUS_E1RM_JUMP_FRACTION) {
                flags += sorted[index].weekStart
            }
        }
        return flags
    }

    private fun rollingMedianPeaks(signals: List<WeeklyTrainingSignal>, index: Int): Double? {
        val half = ROLLING_MEDIAN_SMOOTHING_WEEKS / 2
        val start = (index - half).coerceAtLeast(0)
        val end = (index + half).coerceAtMost(signals.lastIndex)
        val window =
            (start..end).mapNotNull { i ->
                signals[i].peakEstimatedOneRm ?: signals[i].smoothedEstimatedOneRm
            }
        if (window.isEmpty()) return null
        val sorted = window.sorted()
        return sorted[sorted.size / 2]
    }
}
