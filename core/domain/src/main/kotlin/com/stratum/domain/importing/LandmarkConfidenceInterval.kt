package com.stratum.domain.importing

import com.stratum.domain.response.modeling.EpochBlockedBootstrap
import com.stratum.domain.response.modeling.SqrtDoseResponseFit

/**
 * A confidence interval around an estimated dose–response anchor (typically PV).
 *
 * [low] and [high] are weekly fractional hard-set bounds. [method] records how the interval was
 * derived so persistence and UI can distinguish bootstrap bands from pragmatic spreads and
 * prior-only fallbacks.
 */
data class LandmarkConfidenceInterval(
    val low: Double,
    val high: Double,
    val method: IntervalMethod,
) {
    /**
     * `true` when the interval carries real information. `false` when we fell back to priors with
     * no personal signal — the UI omits the band rather than showing an uninformative range.
     */
    val isAvailable: Boolean
        get() = method != IntervalMethod.Prior && high > low

    companion object {
        /** Sentinel for the prior-only fallback path: no personal-data-derived bound. */
        val Unavailable: LandmarkConfidenceInterval =
            LandmarkConfidenceInterval(low = 0.0, high = 0.0, method = IntervalMethod.Prior)
    }
}

/**
 * How a [LandmarkConfidenceInterval] was computed.
 *
 * - [EpochBlockedBootstrap] — empirical 5th–95th percentile band from epoch-blocked resampling of a
 *   [com.stratum.domain.response.modeling.SqrtDoseResponseFit] (400 iterations by default).
 * - [Pragmatic] — approximate spread from confidence tier when data is too sparse for bootstrap.
 * - [Prior] — literature-prior-only fallback; no personal-data-derived bound.
 *
 * Legacy values [DeltaMethod] and [ResidualBootstrap] remain for persisted rows from older builds.
 */
enum class IntervalMethod {
    EpochBlockedBootstrap,
    Pragmatic,
    Prior,
    @Deprecated("Legacy quadratic OLS landmark estimator")
    DeltaMethod,
    @Deprecated("Legacy Banister MRV bootstrap")
    ResidualBootstrap,
}
