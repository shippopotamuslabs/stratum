package com.stratum.domain.response

import java.time.Instant
import kotlin.math.ln

object ChangePointDetector {
    fun binarySegment(signals: List<WeeklyTrainingSignal>): List<Instant> {
        if (signals.size < MIN_BINSEG_SEGMENT_WEEKS * 2) return emptyList()
        val sorted = signals.sortedBy { it.weekStart }
        val values =
            sorted.map { signal ->
                signal.peakEstimatedOneRm
                    ?: signal.smoothedEstimatedOneRm
                    ?: signal.hardSets.toDouble()
            }
        val penalty = BINSEG_PENALTY_MULTIPLIER * ln(values.size.toDouble())
        val totalSse = sse(values)

        var bestSplitIndex = -1
        var bestCost = Double.POSITIVE_INFINITY
        for (splitIndex in MIN_BINSEG_SEGMENT_WEEKS until sorted.size - MIN_BINSEG_SEGMENT_WEEKS + 1) {
            val left = values.subList(0, splitIndex)
            val right = values.subList(splitIndex, values.size)
            val cost = sse(left) + sse(right) + penalty
            if (cost < bestCost) {
                bestCost = cost
                bestSplitIndex = splitIndex
            }
        }
        if (bestSplitIndex < 0 || bestCost >= totalSse + penalty) {
            return emptyList()
        }
        return listOf(sorted[bestSplitIndex].weekStart)
    }

    private fun sse(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.sumOf { value -> (value - mean) * (value - mean) }
    }
}
