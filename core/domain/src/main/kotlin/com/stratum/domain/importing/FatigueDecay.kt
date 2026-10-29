package com.stratum.domain.importing

/** Per-lift fatigue decay pair — nullable so callers fall back to population defaults when absent. */
data class FatigueDecay(
    val slope: Double?,
    val intercept: Double?,
)
