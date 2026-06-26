package com.stratum.domain.response.modeling

/** Tuning constants for the muscle outcome modeling pipeline. */
internal object ResponseModelingConstants {
    const val EWMA_SPAN_WEEKS = 4
    const val DEFAULT_RESPONSE_WINDOW_WEEKS = 4
    val CANDIDATE_LAG_WEEKS = listOf(0, 1, 2)
    const val LIFT_BASELINE_WEEKS = 8
    const val MIN_LIFT_WEEKS_FOR_BASELINE = 3
    const val MIN_INDEX_WEEKS_FOR_MODEL = 12
    const val MIN_PAIRED_POINTS_FOR_MODEL = 8
    const val MIN_VOLUME_BINS = 3
    const val PEAK_DOSE_COVERAGE_MIN = 0.85
    const val COMPOSITION_DRIFT_PENALTY_THRESHOLD = 0.35
    const val PROGRESS_THRESHOLD_LOG = 0.002
    const val PV_RESPONSE_FRACTION = 0.8
    const val PRIOR_BLEND_MIN_WEIGHT = 0.0
    const val PRIOR_BLEND_MAX_WEIGHT = 1.0
    const val HIGH_CONFIDENCE_THRESHOLD = 0.72
    const val MEDIUM_CONFIDENCE_THRESHOLD = 0.42
    const val MIN_RECOVERABLE_WEEKLY_SETS = 1
    const val PLATEAU_VOLUME_STEP_DOWN = 1.0
    const val VOLUME_EPSILON = 1e-6
    const val ROBUST_HUBER_DELTA = 1.5
    const val MONOTONIC_GRID_STEP = 0.5
    const val EXTRACTED_PROFILE_VERSION = 2
}
