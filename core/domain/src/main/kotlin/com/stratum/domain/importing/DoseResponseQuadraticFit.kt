@file:Suppress("MagicNumber") // OLS linear algebra uses fixed indices; documenting them inline.

package com.stratum.domain.importing

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The outcome of a quadratic dose-response OLS fit of weekly progress against weekly hard sets.
 *
 * The model is `progress = a·v² + b·v + c`, fit by ordinary least squares over the per-week
 * points (volume → week-over-week e1RM delta). The dose-response curve for hypertrophy is an
 * inverted-U: progress rises with volume up to a vertex (MAV), then declines as fatigue
 * accumulates past MRV. [DoseResponseQuadraticFit] returns the fitted coefficients, their
 * covariance matrix, and derived landmarks (MAV vertex, MEV lower root, PV Pareto point) with
 * Delta-Method confidence intervals.
 *
 * The fit is rejected (returns `null` from [fit]) when the data does not support an inverted-U:
 * too few points, a non-negative `a` (no vertex / concave-up), a vertex outside the observed
 * volume range, or an R² below [VolumeLandmarkEstimatorConstants.OLS_FIT_MIN_R_SQUARED]. The
 * estimator then falls back to the heuristic/prior path.
 */
data class DoseResponseQuadraticFit(
    val a: Double,
    val b: Double,
    val c: Double,
    val rSquared: Double,
    /** 3×3 symmetric covariance matrix of (a, b, c), row-major. */
    val covariance: List<Double>,
    val residualVariance: Double,
    val mavVertex: Double,
    val mevRoot: Double,
    val pvPareto: Double,
    val mavDeltaVariance: Double,
    val mevDeltaVariance: Double,
    val pvDeltaVariance: Double,
) {
    /**
     * Delta-Method 95% confidence interval for MAV (the vertex). The vertex is `g(β) = -b/(2a)`;
     * its gradient w.r.t. (a, b, c) is `(b/(2a²), -1/(2a), 0)`. Variance = ∇gᵀ·Cov·∇g.
     */
    fun mavInterval(): LandmarkConfidenceInterval {
        if (mavDeltaVariance <= 0.0) return LandmarkConfidenceInterval.Unavailable
        val se = sqrt(mavDeltaVariance)
        val half = VolumeLandmarkEstimatorConstants.Z_95 * se
        return LandmarkConfidenceInterval(
            low = (mavVertex - half).coerceAtLeast(0.0),
            high = mavVertex + half,
            method = IntervalMethod.DeltaMethod,
        )
    }

    /** Delta-Method 95% CI for MEV (lower root). */
    fun mevInterval(): LandmarkConfidenceInterval {
        if (mevDeltaVariance <= 0.0) return LandmarkConfidenceInterval.Unavailable
        val se = sqrt(mevDeltaVariance)
        val half = VolumeLandmarkEstimatorConstants.Z_95 * se
        return LandmarkConfidenceInterval(
            low = (mevRoot - half).coerceAtLeast(0.0),
            high = mevRoot + half,
            method = IntervalMethod.DeltaMethod,
        )
    }

    /** Delta-Method 95% CI for PV (Pareto point). */
    fun pvInterval(): LandmarkConfidenceInterval {
        if (pvDeltaVariance <= 0.0) return LandmarkConfidenceInterval.Unavailable
        val se = sqrt(pvDeltaVariance)
        val half = VolumeLandmarkEstimatorConstants.Z_95 * se
        return LandmarkConfidenceInterval(
            low = (pvPareto - half).coerceAtLeast(0.0),
            high = pvPareto + half,
            method = IntervalMethod.DeltaMethod,
        )
    }

    companion object {
        /**
         * Fit `progress = a·v² + b·v + c` via OLS normal equations. Returns `null` when the fit
         * is rejected (non-inverted-U, implausible vertex, low R², or singular design matrix).
         *
         * [points] are (volume, progress) pairs. The progress value is typically the
         * week-over-week e1RM delta, so a flat/stagnant week is ~0.
         */
        @Suppress("ReturnCount") // guard-clause rejection is the intended fit-validation pattern
        fun fit(points: List<VolumeDosePoint>): DoseResponseQuadraticFit? {
            if (points.size < VolumeLandmarkEstimatorConstants.MIN_WEEKS_FOR_OLS) return null
            val xs = points.map { it.volume }
            val distinctVolumes = xs.distinct().size
            if (distinctVolumes < VolumeLandmarkEstimatorConstants.MIN_VOLUME_SPREAD_FOR_OLS) return null

            // Normal equations for the quadratic: solve (XᵀX) β = Xᵀy where X = [v², v, 1].
            val xtx = Array(3) { DoubleArray(3) }
            val xty = DoubleArray(3)
            for (p in points) {
                val v = p.volume
                val v2 = v * v
                val design = doubleArrayOf(v2, v, 1.0)
                for (i in 0..2) {
                    for (j in 0..2) {
                        xtx[i][j] += design[i] * design[j]
                    }
                    xty[i] += design[i] * p.progress
                }
            }
            val xtxInv = invert3(xtx) ?: return null
            val beta = DoubleArray(3)
            for (i in 0..2) {
                for (j in 0..2) {
                    beta[i] += xtxInv[i][j] * xty[j]
                }
            }
            val a = beta[0]
            val b = beta[1]
            val c = beta[2]

            // Inverted-U requires a negative leading coefficient and a vertex in-range.
            if (a >= 0.0) return null
            val vertex = -b / (2.0 * a)
            val vMin = xs.min()
            val vMax = xs.max()
            if (vertex <= vMin || vertex >= vMax) return null

            // Residual variance and R².
            val yMean = points.map { it.progress }.average()
            var ssTot = 0.0
            var ssRes = 0.0
            for (p in points) {
                val predicted = a * p.volume * p.volume + b * p.volume + c
                val res = p.progress - predicted
                ssRes += res * res
                ssTot += (p.progress - yMean) * (p.progress - yMean)
            }
            if (ssTot == 0.0) return null
            val rSquared = 1.0 - ssRes / ssTot
            if (rSquared < VolumeLandmarkEstimatorConstants.OLS_FIT_MIN_R_SQUARED) return null
            val dof = (points.size - 3).coerceAtLeast(1)
            val residualVariance = ssRes / dof

            // Covariance(β) = σ² (XᵀX)⁻¹.
            val cov = List(9) { idx -> xtxInv[idx / 3][idx % 3] * residualVariance }

            // MAV = vertex = -b/(2a); gradient g = (b/(2a²), -1/(2a), 0).
            val mavGrad = doubleArrayOf(b / (2.0 * a * a), -1.0 / (2.0 * a), 0.0)
            val mavVar = quadraticForm(mavGrad, cov)

            // MEV = lower root of the ascending limb where progress crosses 0.
            // Root: v = (-b - sqrt(b²-4ac)) / (2a). For a<0 the lower root uses the '-' branch.
            val disc = b * b - 4.0 * a * c
            val mev = if (disc < 0.0) {
                vMin
            } else {
                val sqrtDisc = sqrt(disc)
                val root1 = (-b - sqrtDisc) / (2.0 * a)
                val root2 = (-b + sqrtDisc) / (2.0 * a)
                // The ascending limb's lower root is the smaller positive root.
                listOf(root1, root2).filter { it in 0.0..vertex }.minOrNull() ?: vMin
            }
            val mevGrad = mevGradient(a, b, c, disc, mev)
            val mevVar = if (mevGrad != null) quadraticForm(mevGrad, cov) else 0.0

            // PV = Pareto point: volume on the ascending limb reaching PV_PARETO_FRACTION of peak
            // progress. Peak progress is at the vertex: peak = c - b²/(4a). Pareto progress target
            // = fraction * peak. Solve a·v² + b·v + c = target on the ascending limb.
            val peakProgress = c - b * b / (4.0 * a)
            val paretoTarget = VolumeLandmarkEstimatorConstants.PV_PARETO_FRACTION * peakProgress
            val pv = solveAscendingLimb(a, b, c, paretoTarget, vertex, vMin)
            val pvGrad = if (disc >= 0.0 && abs(a) > 1e-12) {
                pvGradient(a, b, c, paretoTarget, pv)
            } else {
                null
            }
            val pvVar = if (pvGrad != null) quadraticForm(pvGrad, cov) else 0.0

            return DoseResponseQuadraticFit(
                a = a,
                b = b,
                c = c,
                rSquared = rSquared,
                covariance = cov,
                residualVariance = residualVariance,
                mavVertex = vertex,
                mevRoot = mev,
                pvPareto = pv,
                mavDeltaVariance = mavVar,
                mevDeltaVariance = mevVar,
                pvDeltaVariance = pvVar,
            )
        }

        /**
         * Gradient of MEV (lower root) w.r.t. (a, b, c). The root v* = (-b - sqrt(D))/(2a) with
         * D = b² - 4ac. Using implicit differentiation of the constraint F = a·v² + b·v + c = 0:
         * dv/da = -v²/F_v, dv/db = -v/F_v, dv/dc = -1/F_v, where F_v = 2a·v + b. The [c]
         * coefficient does not appear in the gradient (it shifts F by a constant, not the root's
         * sensitivity), so it is marked unused.
         */
        @Suppress("UnusedParameter")
        private fun mevGradient(a: Double, b: Double, c: Double, disc: Double, v: Double): DoubleArray? {
            if (disc < 0.0) return null
            val fv = 2.0 * a * v + b
            if (abs(fv) < 1e-12) return null
            return doubleArrayOf(-v * v / fv, -v / fv, -1.0 / fv)
        }

        /**
         * Gradient of PV (ascending-limb solution of a·v² + b·v + c = target) w.r.t. (a, b, c).
         * With [target] and [c] independent of β, the implicit-function result is the same form as
         * the root gradient but on the ascending limb (F_v = 2a·v + b > 0 near the lower root).
         */
        @Suppress("UnusedParameter")
        private fun pvGradient(a: Double, b: Double, c: Double, target: Double, v: Double): DoubleArray? {
            val fv = 2.0 * a * v + b
            if (abs(fv) < 1e-12) return null
            return doubleArrayOf(-v * v / fv, -v / fv, -1.0 / fv)
        }

        /** Solve a·v² + b·v + c = target on the ascending limb (v ≤ vertex). */
        private fun solveAscendingLimb(a: Double, b: Double, c: Double, target: Double, vertex: Double, vMin: Double): Double {
            val rhs = c - target
            val disc = b * b - 4.0 * a * rhs
            if (disc < 0.0) return vMin
            val sqrtDisc = sqrt(disc)
            val root1 = (-b - sqrtDisc) / (2.0 * a)
            val root2 = (-b + sqrtDisc) / (2.0 * a)
            val candidates = listOf(root1, root2).filter { it in 0.0..vertex }
            return candidates.minOrNull() ?: vMin
        }

        /** ∇gᵀ · Cov · ∇g for a 3-vector gradient and 3×3 covariance (row-major list). */
        private fun quadraticForm(grad: DoubleArray, cov: List<Double>): Double {
            var sum = 0.0
            for (i in 0..2) {
                for (j in 0..2) {
                    sum += grad[i] * cov[i * 3 + j] * grad[j]
                }
            }
            return sum
        }

        /** Invert a 3×3 symmetric matrix; returns `null` if singular. */
        private fun invert3(m: Array<DoubleArray>): Array<DoubleArray>? {
            val a = m[0][0]; val b = m[0][1]; val c = m[0][2]
            val d = m[1][0]; val e = m[1][1]; val f = m[1][2]
            val g = m[2][0]; val h = m[2][1]; val i = m[2][2]
            val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
            if (abs(det) < 1e-15) return null
            val invDet = 1.0 / det
            return arrayOf(
                doubleArrayOf((e * i - f * h) * invDet, (c * h - b * i) * invDet, (b * f - c * e) * invDet),
                doubleArrayOf((f * g - d * i) * invDet, (a * i - c * g) * invDet, (c * d - a * f) * invDet),
                doubleArrayOf((d * h - e * g) * invDet, (b * g - a * h) * invDet, (a * e - b * d) * invDet),
            )
        }
    }
}

/** A single (volume, progress) observation for the quadratic dose-response fit. */
data class VolumeDosePoint(val volume: Double, val progress: Double)
