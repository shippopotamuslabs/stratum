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

    // --- Algorithmic Volume Landmarks: quadratic OLS + Banister fit thresholds ---

    /** Minimum anchored weekly points required to attempt the quadratic dose-response fit. */
    const val MIN_WEEKS_FOR_OLS = 6

    /** Minimum distinct weekly hard-set values to fit a meaningful quadratic (need spread on x). */
    const val MIN_VOLUME_SPREAD_FOR_OLS = 4

    /** Minimum R² for the quadratic/Banister fit to be accepted; below this we fall back. */
    const val OLS_FIT_MIN_R_SQUARED = 0.3

    /** Minimum weekly points required to attempt the Banister fitness-fatigue fit. */
    const val BANISTER_MIN_WEEKS = 8

    /** MRV from the Banister fit is rejected if it exceeds observed max × this factor. */
    const val BANISTER_MRV_MAX_FACTOR = 1.5

    /**
     * Residual bootstrap iterations for the Banister MRV confidence interval. Reduced from the
     * document's 10,000 for mobile performance (the fit runs on-device, offline); 1,000 gives a
     * stable 95% band without blocking the import flow. Deterministic via a fixed seed.
     */
    const val BOOTSTRAP_ITERATIONS = 1000

    /** 95% confidence z-score for Delta-Method intervals. */
    const val Z_95 = 1.96

    /** Pareto fraction: PV is the ascending-limb volume reaching this fraction of peak progress. */
    const val PV_PARETO_FRACTION = 0.8

    // Pragmatic-interval spread fractions of the prior half-band, by confidence tier. The band
    // widens as confidence drops: ±15% at High, ±35% at Medium, ±60% at Low. Used by the
    // pragmatic-interval fallback when no statistical fit (Delta Method / bootstrap) is available.
    const val PRAGMATIC_INTERVAL_SPREAD_HIGH = 0.15
    const val PRAGMATIC_INTERVAL_SPREAD_MEDIUM = 0.35
    const val PRAGMATIC_INTERVAL_SPREAD_LOW = 0.60
}
