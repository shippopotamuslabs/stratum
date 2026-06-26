@file:Suppress("MagicNumber") // Fitness-fatigue decay constants and solver iterations are inline-documented.

package com.stratum.domain.importing

import kotlin.math.abs
import kotlin.random.Random

/**
 * Simplified Banister fitness-fatigue model fit for MRV estimation.
 *
 * The Banister model decomposes the training response into a slow-decay *fitness* integral and
 * a fast-decay *fatigue* integral of the weekly training load:
 *   `progress(t) = k1 · fitness(t) - k2 · fatigue(t)`
 * where fitness and fatigue are exponentially-weighted cumulative sums of weekly hard sets with
 * decay constants τ1 (slow, ~weeks) and τ2 (fast, ~days→1 week).
 *
 * **MRV** is the steady-state weekly volume where net progress crosses zero — the volume at which
 * accumulated fatigue exactly cancels accumulated fitness. Solving the steady state gives:
 *   `v* = k1·τ1·(1 - e^(-T/τ1)) / (k2·τ2·(1 - e^(-T/τ2)))` per week, where T is the week length.
 * With T normalized so τ1, τ2 are in weeks, this reduces to `v* = (k1·τ1) / (k2·τ2)` in the long
 * run. The fit estimates (k1, k2, τ1, τ2) by grid search over the decay constants plus closed-form
 * least squares for the gains, then derives MRV and a residual-bootstrap confidence interval.
 *
 * The fit is rejected (returns `null`) when data is too short, the fit is degenerate (k2 ≤ 0,
 * no fatigue component), or MRV falls outside the plausible volume range. The estimator then
 * falls back to the heuristic MRV path with a pragmatic interval.
 */
data class BanisterFitnessFatigueFit(
    val k1: Double,
    val k2: Double,
    val tau1: Double,
    val tau2: Double,
    val rSquared: Double,
    val mrv: Double,
    val bootstrapLow: Double,
    val bootstrapHigh: Double,
) {
    fun mrvInterval(): LandmarkConfidenceInterval =
        if (bootstrapHigh > bootstrapLow) {
            LandmarkConfidenceInterval(
                low = bootstrapLow.coerceAtLeast(0.0),
                high = bootstrapHigh,
                method = IntervalMethod.ResidualBootstrap,
            )
        } else {
            LandmarkConfidenceInterval.Unavailable
        }

    companion object {
        private const val SEED = 42L
        private const val GRID_TAU1_MIN = 2.0
        private const val GRID_TAU1_MAX = 12.0
        private const val GRID_TAU1_STEP = 1.0
        private const val GRID_TAU2_MIN = 0.5
        private const val GRID_TAU2_MAX = 3.0
        private const val GRID_TAU2_STEP = 0.25

        /**
         * Fit the simplified Banister model to the weekly (volume, progress) series. Returns
         * `null` when the fit is rejected.
         */
        fun fit(points: List<VolumeDosePoint>): BanisterFitnessFatigueFit? {
            if (points.size < VolumeLandmarkEstimatorConstants.BANISTER_MIN_WEEKS) return null
            val volumes = points.map { it.volume }
            val progress = points.map { it.progress }
            val vMin = volumes.min()
            val vMax = volumes.max()

            var best: BanisterFitnessFatigueFit? = null
            var bestSse = Double.MAX_VALUE

            var tau1 = GRID_TAU1_MIN
            while (tau1 <= GRID_TAU1_MAX) {
                var tau2 = GRID_TAU2_MIN
                while (tau2 <= GRID_TAU2_MAX) {
                    if (tau2 >= tau1) { tau2 += GRID_TAU2_STEP; continue }
                    val fitness = exponentialIntegral(volumes, tau1)
                    val fatigue = exponentialIntegral(volumes, tau2)
                    // Closed-form LS for (k1, k2) given fitness/fatigue: minimize ||progress - k1·f - k2·g||.
                    val (k1, k2, sse) = solveGains(fitness, fatigue, progress)
                    if (k2 > 0.0 && sse < bestSse) {
                        val yMean = progress.average()
                        val ssTot = progress.sumOf { (it - yMean) * (it - yMean) }
                        val rSq = if (ssTot > 0.0) 1.0 - sse / ssTot else 0.0
                        val mrv = (k1 * tau1) / (k2 * tau2)
                        if (mrv in vMin..(vMax * VolumeLandmarkEstimatorConstants.BANISTER_MRV_MAX_FACTOR)) {
                            best = BanisterFitnessFatigueFit(k1, k2, tau1, tau2, rSq, mrv, 0.0, 0.0)
                            bestSse = sse
                        }
                    }
                    tau2 += GRID_TAU2_STEP
                }
                tau1 += GRID_TAU1_STEP
            }

            val chosen = best ?: return null
            if (chosen.rSquared < VolumeLandmarkEstimatorConstants.OLS_FIT_MIN_R_SQUARED) return null

            val (low, high) = residualBootstrap(points, chosen, volumes)
            return chosen.copy(bootstrapLow = low, bootstrapHigh = high)
        }

        /**
         * Exponentially-weighted cumulative sum of weekly volume with decay constant [tau]
         * (in weeks): `s(t) = Σ v(s) · exp(-(t - s)/tau)` over weeks s ≤ t.
         */
        private fun exponentialIntegral(volumes: List<Double>, tau: Double): List<Double> {
            val out = DoubleArray(volumes.size)
            var decay = kotlin.math.exp(-1.0 / tau)
            for (i in volumes.indices) {
                out[i] = if (i == 0) volumes[0] else volumes[i] + decay * out[i - 1]
            }
            return out.toList()
        }

        /**
         * Closed-form least squares for gains (k1, k2) given fitness/fatigue series. Solves the
         * 2×2 normal equations. Returns (k1, k2, sse).
         */
        private fun solveGains(
            fitness: List<Double>,
            fatigue: List<Double>,
            progress: List<Double>,
        ): Triple<Double, Double, Double> {
            var sff = 0.0; var sfg = 0.0; var sgg = 0.0
            var sfy = 0.0; var sgy = 0.0
            for (i in progress.indices) {
                val f = fitness[i]; val g = fatigue[i]; val y = progress[i]
                sff += f * f; sfg += f * g; sgg += g * g
                sfy += f * y; sgy += g * y
            }
            val det = sff * sgg - sfg * sfg
            if (abs(det) < 1e-15) return Triple(0.0, 0.0, Double.MAX_VALUE)
            val k1 = (sgg * sfy - sfg * sgy) / det
            val k2 = (sff * sgy - sfg * sfy) / det
            var sse = 0.0
            for (i in progress.indices) {
                val pred = k1 * fitness[i] - k2 * fatigue[i]
                sse += (progress[i] - pred) * (progress[i] - pred)
            }
            return Triple(k1, k2, sse)
        }

        /**
         * Residual bootstrap: refit on perturbed progress series (predicted + resampled residuals)
         * and collect the distribution of MRV estimates. Returns the 2.5/97.5 percentile band.
         * Iteration count is [VolumeLandmarkEstimatorConstants.BOOTSTRAP_ITERATIONS] (reduced from
         * the document's 10k for mobile performance; documented in the constants file).
         */
        private fun residualBootstrap(
            points: List<VolumeDosePoint>,
            fit: BanisterFitnessFatigueFit,
            volumes: List<Double>,
        ): Pair<Double, Double> {
            val fitness = exponentialIntegral(volumes, fit.tau1)
            val fatigue = exponentialIntegral(volumes, fit.tau2)
            val residuals = DoubleArray(points.size) { i ->
                points[i].progress - (fit.k1 * fitness[i] - fit.k2 * fatigue[i])
            }
            val rng = Random(SEED)
            val mrvs = DoubleArray(VolumeLandmarkEstimatorConstants.BOOTSTRAP_ITERATIONS) {
                val perturbed = DoubleArray(points.size) { i ->
                    (fit.k1 * fitness[i] - fit.k2 * fatigue[i]) + residuals[rng.nextInt(residuals.size)]
                }
                val bootPoints = points.indices.map { i -> VolumeDosePoint(volumes[i], perturbed[i]) }
                refitMrv(bootPoints) ?: fit.mrv
            }
            mrvs.sort()
            val n = mrvs.size
            val low = mrvs[(0.025 * n).toInt().coerceIn(0, n - 1)]
            val high = mrvs[(0.975 * n).toInt().coerceIn(0, n - 1)]
            return Pair(low, high)
        }

        /** Refit MRV on a perturbed series (single grid iteration is too slow; reuse τ's, re-solve gains). */
        private fun refitMrv(points: List<VolumeDosePoint>): Double? {
            val volumes = points.map { it.volume }
            val progress = points.map { it.progress }
            val fitness = exponentialIntegral(volumes, GRID_TAU1_MIN + 3.0)
            val fatigue = exponentialIntegral(volumes, GRID_TAU2_MIN + 0.5)
            val (k1, k2, _) = solveGains(fitness, fatigue, progress)
            if (k2 <= 0.0) return null
            val tau1 = GRID_TAU1_MIN + 3.0
            val tau2 = GRID_TAU2_MIN + 0.5
            val mrv = (k1 * tau1) / (k2 * tau2)
            val vMin = volumes.min()
            val vMax = volumes.max()
            return if (mrv in vMin..(vMax * VolumeLandmarkEstimatorConstants.BANISTER_MRV_MAX_FACTOR)) mrv else null
        }
    }
}
