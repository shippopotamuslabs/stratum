package com.stratum.domain.importing

/**
 * A confidence interval around an estimated volume landmark (MEV/MAV/MRV/PV).
 *
 * [low] and [high] are weekly hard-set bounds. [method] records how the interval was derived so
 * the UI and prompt builder can distinguish a statistically-grounded bound (Delta Method on the
 * quadratic OLS fit, or residual bootstrapping on the Banister fit) from an approximate bound
 * derived when data is too sparse for a real fit ([IntervalMethod.Pragmatic]) or when we fell
 * back to literature priors ([IntervalMethod.Prior]).
 *
 * Volume is counted in hard sets; the bounds are fractional (e.g. 6.3–9.8). The estimator rounds
 * the point estimates up to whole-set targets, but the interval itself keeps its fractional
 * precision so the UI can show the genuine spread rather than a ceil-distorted band.
 */
data class LandmarkConfidenceInterval(
    val low: Double,
    val high: Double,
    val method: IntervalMethod,
) {
    /**
     * `true` when the interval carries real information (a fit or a pragmatic spread). `false`
     * when we fell all the way back to priors with no personal signal — the UI omits the band in
     * that case rather than showing a wide, uninformative range.
     */
    val isAvailable: Boolean
        get() = method != IntervalMethod.Prior && high > low

    companion object {
        /**
         * Sentinel for the prior-only fallback path: no personal-data-derived bound. Mapped to an
         * empty/omitted interval in the UI.
         */
        val Unavailable: LandmarkConfidenceInterval =
            LandmarkConfidenceInterval(low = 0.0, high = 0.0, method = IntervalMethod.Prior)
    }
}

/**
 * How a [LandmarkConfidenceInterval] was computed.
 *
 * - [DeltaMethod] — analytical bound from the quadratic OLS dose-response fit, via the Delta
 *   Method on the coefficient covariance matrix (MEV/MAV/PV).
 * - [ResidualBootstrap] — empirical bound from residual bootstrapping of the Banister
 *   fitness-fatigue fit (MRV).
 * - [Pragmatic] — approximate bound derived from the estimator confidence score, prior band
 *   width, and observation spread when data is too sparse for a real fit.
 * - [Prior] — literature-prior-only fallback; no personal-data-derived bound.
 */
enum class IntervalMethod { DeltaMethod, ResidualBootstrap, Pragmatic, Prior }
