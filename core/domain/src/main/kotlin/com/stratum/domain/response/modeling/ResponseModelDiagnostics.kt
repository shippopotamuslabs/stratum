@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.importing.LandmarkConfidenceTier
import kotlin.math.abs

internal object ResponseModelDiagnostics {
    fun confidenceScore(
        indexWeekCount: Int,
        pairedPointCount: Int,
        volumeBinCount: Int,
        peakDoseCoverage: Double,
        compositionStability: Double,
        predictiveScore: Double?,
        residualError: Double?,
        rejectionReasons: List<String>,
    ): Double {
        var score = 0.0
        var weightSum = 0.0
        score += (indexWeekCount.coerceAtMost(40).toDouble() / 40.0) * 0.25
        weightSum += 0.25
        score += (pairedPointCount.coerceAtMost(24).toDouble() / 24.0) * 0.25
        weightSum += 0.25
        score += (volumeBinCount.coerceAtMost(8).toDouble() / 8.0) * 0.15
        weightSum += 0.15
        score += peakDoseCoverage.coerceIn(0.0, 1.0) * 0.15
        weightSum += 0.15
        score += compositionStability.coerceIn(0.0, 1.0) * 0.10
        weightSum += 0.10
        predictiveScore?.let {
            val normalized = (it + 1.0).coerceIn(0.0, 1.0)
            score += normalized * 0.10
            weightSum += 0.10
        }
        residualError?.let {
            val normalized = (1.0 / (1.0 + abs(it))).coerceIn(0.0, 1.0)
            score += normalized * 0.05
            weightSum += 0.05
        }
        var raw = if (weightSum > 0.0) score / weightSum else 0.0
        if (rejectionReasons.isNotEmpty()) {
            raw *= 0.65
        }
        return raw.coerceIn(0.0, 1.0)
    }

    fun confidenceTier(score: Double): LandmarkConfidenceTier =
        when {
            score >= ResponseModelingConstants.HIGH_CONFIDENCE_THRESHOLD -> LandmarkConfidenceTier.High
            score >= ResponseModelingConstants.MEDIUM_CONFIDENCE_THRESHOLD -> LandmarkConfidenceTier.Medium
            else -> LandmarkConfidenceTier.Low
        }
}
