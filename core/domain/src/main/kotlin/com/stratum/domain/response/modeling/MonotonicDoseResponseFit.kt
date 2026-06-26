@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.importing.VolumeDosePoint
import kotlin.math.abs

internal data class MonotonicDoseResponseFit(
    val mev: Double,
    val mav: Double,
    val pv: Double,
    val mrv: Double,
    val rSquared: Double,
    val residualError: Double,
) {
    companion object {
        fun fit(points: List<VolumeDosePoint>): MonotonicDoseResponseFit? {
            if (points.size < ResponseModelingConstants.MIN_PAIRED_POINTS_FOR_MODEL) return null
            val doses = points.map { it.volume }
            val minDose = doses.min()
            val maxDose = doses.max()
            if (maxDose - minDose < ResponseModelingConstants.MONOTONIC_GRID_STEP) return null

            val grid = buildGrid(minDose, maxDose)
            var best: MonotonicDoseResponseFit? = null
            var bestScore = Double.NEGATIVE_INFINITY
            for (mav in grid) {
                val candidate = fitForMav(points, mav)
                val score = candidate.rSquared - candidate.residualError
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            return best
        }

        private fun buildGrid(minDose: Double, maxDose: Double): List<Double> {
            val values = mutableListOf<Double>()
            var current = minDose
            while (current <= maxDose + ResponseModelingConstants.VOLUME_EPSILON) {
                values += current
                current += ResponseModelingConstants.MONOTONIC_GRID_STEP
            }
            return values
        }

        private fun fitForMav(points: List<VolumeDosePoint>, mav: Double): MonotonicDoseResponseFit {
            val ascending = points.filter { it.volume <= mav }
            val descending = points.filter { it.volume >= mav }
            val peakResponse = points.maxOf { predicted(mav, it.volume, ascending, descending) }
            val mev =
                ascending.firstOrNull { it.progress >= ResponseModelingConstants.PROGRESS_THRESHOLD_LOG }?.volume
                    ?: ascending.minOfOrNull { it.volume }
                    ?: points.minOf { it.volume }
            val pvTarget = peakResponse * ResponseModelingConstants.PV_RESPONSE_FRACTION
            val pv =
                ascending.firstOrNull { predicted(mav, it.volume, ascending, descending) >= pvTarget }?.volume
                    ?: mev
            val mrv =
                descending.firstOrNull { predicted(mav, it.volume, ascending, descending) <= 0.0 }?.volume
                    ?: points.maxOf { it.volume }
            val predictions = points.map { predicted(mav, it.volume, ascending, descending) }
            val mean = points.map { it.progress }.average()
            val ssTot = points.sumOf { (it.progress - mean) * (it.progress - mean) }
            val residuals = points.indices.map { index -> points[index].progress - predictions[index] }
            val ssRes = residuals.sumOf { it * it }
            val rSquared = if (ssTot <= 0.0) 0.0 else (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0)
            return MonotonicDoseResponseFit(
                mev = mev,
                mav = mav,
                pv = pv,
                mrv = mrv,
                rSquared = rSquared,
                residualError = ssRes / points.size.coerceAtLeast(1),
            )
        }

        private fun predicted(
            mav: Double,
            dose: Double,
            ascending: List<VolumeDosePoint>,
            descending: List<VolumeDosePoint>,
        ): Double {
            val ascendingFit = piecewiseSlope(ascending)
            val descendingFit = piecewiseSlope(descending)
            return when {
                dose <= mav -> ascendingFit.first + ascendingFit.second * dose
                else -> {
                    val atVertex = ascendingFit.first + ascendingFit.second * mav
                    atVertex + descendingFit.second * (dose - mav)
                }
            }
        }

        private fun piecewiseSlope(points: List<VolumeDosePoint>): Pair<Double, Double> {
            if (points.size < 2) {
                val only = points.firstOrNull()?.progress ?: 0.0
                return only to 0.0
            }
            val xs = points.map { it.volume }
            val ys = points.map { it.progress }
            val meanX = xs.average()
            val meanY = ys.average()
            val num = xs.indices.sumOf { index -> (xs[index] - meanX) * (ys[index] - meanY) }
            val den = xs.sumOf { (it - meanX) * (it - meanX) }
            val slope = if (abs(den) < 1e-9) 0.0 else num / den
            val intercept = meanY - slope * meanX
            return intercept to slope
        }
    }
}
