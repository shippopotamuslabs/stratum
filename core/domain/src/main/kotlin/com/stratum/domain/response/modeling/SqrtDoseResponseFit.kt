@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.importing.VolumeDosePoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Weekly dose–response fit: `response ≈ intercept + slope × √volume`.
 *
 * Aligns with Pelland et al. (2025) monotonic diminishing-returns meta-regression. Used for posture
 * classification and epoch-blocked bootstrap confidence bands — not personal MEV/MAV/MRV landmarks.
 */
data class SqrtDoseResponseFit(
    val intercept: Double,
    val slope: Double,
    val rSquared: Double,
    val residualError: Double,
    val marginalSlopeAt: (Double) -> Double = { dose ->
        if (dose <= 0.0) 0.0 else slope / (2.0 * sqrt(dose))
    },
) {
    fun predicted(volume: Double): Double =
        if (volume <= 0.0) intercept else intercept + slope * sqrt(volume)

    companion object {
        private const val MIN_POINTS = 4

        fun fit(points: List<VolumeDosePoint>): SqrtDoseResponseFit? {
            if (points.size < MIN_POINTS) return null
            val xs = points.map { sqrt(it.volume.coerceAtLeast(0.0)) }
            val ys = points.map { it.progress }
            if (xs.distinct().size < 2) return null

            val meanX = xs.average()
            val meanY = ys.average()
            val num = xs.indices.sumOf { i -> (xs[i] - meanX) * (ys[i] - meanY) }
            val den = xs.sumOf { (it - meanX) * (it - meanX) }
            if (abs(den) < 1e-12) return null

            val slope = num / den
            val intercept = meanY - slope * meanX
            val predictions = xs.indices.map { i -> intercept + slope * xs[i] }
            val ssTot = ys.sumOf { (it - meanY) * (it - meanY) }
            val ssRes = ys.indices.sumOf { i -> val r = ys[i] - predictions[i]; r * r }
            if (ssTot <= 0.0) return null
            val rSquared = (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0)

            return SqrtDoseResponseFit(
                intercept = intercept,
                slope = slope,
                rSquared = rSquared,
                residualError = ssRes / points.size.coerceAtLeast(1),
            )
        }
    }
}

/**
 * Epoch-blocked bootstrap confidence band for [SqrtDoseResponseFit] at a reference dose.
 * Resamples whole epochs with replacement to preserve within-epoch autocorrelation.
 */
data class BootstrapConfidenceBand(
    val low: Double,
    val high: Double,
    val pointEstimate: Double,
)

object EpochBlockedBootstrap {
    private const val DEFAULT_ITERATIONS = 400

    fun predictionBand(
        points: List<VolumeDosePoint>,
        epochIds: List<Int>,
        referenceDose: Double,
        iterations: Int = DEFAULT_ITERATIONS,
    ): BootstrapConfidenceBand? {
        if (points.size != epochIds.size || points.size < ResponseModelingConstants.MIN_PAIRED_POINTS_FOR_MODEL) {
            return null
        }
        val epochs = epochIds.distinct()
        if (epochs.size < 2) return null

        val byEpoch = epochs.associateWith { epoch -> points.indices.filter { epochIds[it] == epoch } }
        val baseFit = SqrtDoseResponseFit.fit(points) ?: return null
        val basePred = baseFit.predicted(referenceDose)

        val rng = kotlin.random.Random(42)
        val samples = DoubleArray(iterations)
        repeat(iterations) { iter ->
            val resampled =
                buildList {
                    repeat(epochs.size) {
                        val epoch = epochs[rng.nextInt(epochs.size)]
                        byEpoch[epoch]?.forEach { index -> add(points[index]) }
                    }
                }
            val fit = SqrtDoseResponseFit.fit(resampled)
            samples[iter] = fit?.predicted(referenceDose) ?: basePred
        }
        samples.sort()
        val lowIdx = (0.05 * (iterations - 1)).toInt().coerceIn(0, iterations - 1)
        val highIdx = (0.95 * (iterations - 1)).toInt().coerceIn(0, iterations - 1)
        return BootstrapConfidenceBand(
            low = samples[lowIdx],
            high = samples[highIdx],
            pointEstimate = basePred,
        )
    }
}
