package com.stratum.domain.importing

/** Shared tuning constants for dose-response landmark fitting in the response modeling pipeline. */
internal object VolumeLandmarkEstimatorConstants {
    const val MIN_RECOVERABLE_WEEKLY_SETS = 1

    const val MIN_WEEKS_FOR_OLS = 6

    const val MIN_VOLUME_SPREAD_FOR_OLS = 4

    const val OLS_FIT_MIN_R_SQUARED = 0.3

    const val Z_95 = 1.96

    const val PV_PARETO_FRACTION = 0.8

    const val PRAGMATIC_INTERVAL_SPREAD_HIGH = 0.15
    const val PRAGMATIC_INTERVAL_SPREAD_MEDIUM = 0.35
    const val PRAGMATIC_INTERVAL_SPREAD_LOW = 0.60
}
