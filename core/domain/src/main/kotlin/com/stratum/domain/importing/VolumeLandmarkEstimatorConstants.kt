package com.stratum.domain.importing

/** Tuning constants for [VolumeLandmarkEstimator] confidence scoring and blending. */
internal object VolumeLandmarkEstimatorConstants {
    const val MIN_WEEKS_FOR_OBSERVATION = 8
    const val STAGNATION_DELTA_LBS = 0.25
    const val PLAUSIBLE_LOW_MULTIPLIER = 0.4
    const val PLAUSIBLE_HIGH_MULTIPLIER = 1.6
    const val IMPLAUSIBLE_CONFIDENCE_PENALTY = 0.45

    const val CONFIDENCE_WEEK_CAP = 26
    const val CONFIDENCE_WEEK_WEIGHT = 0.35
    const val CONFIDENCE_VOLUME_SPREAD_CAP = 8
    const val CONFIDENCE_VOLUME_SPREAD_WEIGHT = 0.25
    const val CONFIDENCE_RIR_WEIGHT = 0.20
    const val CONFIDENCE_RSQUARED_WEIGHT = 0.15
    const val CONFIDENCE_WEEKLY_POINTS_CAP = 12
    const val CONFIDENCE_WEEKLY_POINTS_WEIGHT = 0.05

    const val HIGH_CONFIDENCE_THRESHOLD = 0.72
    const val MEDIUM_CONFIDENCE_THRESHOLD = 0.42

    const val MIN_RECOVERABLE_WEEKLY_SETS = 1
    const val PLATEAU_VOLUME_STEP_DOWN = 1

    /** Half-window for centered rolling-mean smoothing of weekly e1RM deltas (total window = 2×+1). */
    const val DELTA_SMOOTHING_WINDOW = 1

    /** Volume comparison tolerance for "at the observed ceiling" checks (handles float drift). */
    const val VOLUME_EPSILON = 1e-6

    /**
     * Weight given to the population prior MRV when the user was still progressing at their
     * observed volume ceiling. Higher = more inferential headroom above the observed max; the
     * blended estimate never exceeds the prior mid, so this is bounded inference, not free
     * extrapolation. 0.5 means "split the difference between observed max and prior mid."
     */
    const val CEILING_BLEND_TOWARD_PRIOR = 0.5
}
