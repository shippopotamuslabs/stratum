package com.stratum.domain.response

const val MAX_EPOCH_GAP_DAYS = 42
const val PRIMARY_JACCARD_CHANGE_THRESHOLD = 0.75
const val ROLLING_PRIMARY_WINDOW_WEEKS = 4
const val HIGH_RIR_WARMUP_THRESHOLD = 4
const val LOW_RELATIVE_LOAD_FRACTION = 0.65
const val ROLLING_MEDIAN_SMOOTHING_WEEKS = 3
const val MAD_OUTLIER_THRESHOLD = 3.5
const val SUSPICIOUS_E1RM_JUMP_FRACTION = 0.15
const val MIN_BINSEG_SEGMENT_WEEKS = 6
const val BINSEG_PENALTY_MULTIPLIER = 4.0
const val DELOAD_VOLUME_FRACTION = 0.50
const val DELOAD_WEIGHT_DROP_FRACTION = 0.15
const val DELOAD_VOLUME_STEADY_BAND = 0.20
const val MIN_ANCHOR_SESSIONS = 8
const val MIN_ANCHOR_WEEKS = 6
const val MIN_LIFT_SESSIONS_FOR_EFFECTIVENESS = 6
const val MIN_EPOCH_WEEKS_FOR_CORRELATION = 8
const val MIN_WEEKLY_POINTS_FOR_OLS = 4
const val RIR_COVERAGE_THRESHOLD = 0.70
const val HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN = 0.65
const val EFFECTIVENESS_RANK_MARGIN = 0.20
const val EFFECTIVENESS_R2_WEIGHT = 0.6
const val EFFECTIVENESS_SESSION_WEIGHT = 0.4
const val MIN_PEERS_FOR_PATTERN_RANK = 2
const val PROVEN_EPOCH_AGREEMENT_COUNT = 2
/**
 * Recency half-life scale (days) for anchor-lift scoring — **not** the worker re-extract cadence.
 * @see com.stratum.domain.response.AnchorLiftSelector
 */
const val RE_EXTRACT_MIN_INTERVAL_DAYS = 28

/**
 * Minimum completed workouts (program + ad-hoc, not rest days) since [ResponseProfileMetadata.lastExtractedAt]
 * before the response-modeling worker re-runs the full extract. Preferable to a calendar interval:
 * time off does not trigger a no-op re-extract, and sparse training waits until there is new signal.
 */
const val RE_EXTRACT_MIN_COMPLETED_WORKOUTS = 20
const val REGRESSION_DENOM_EPSILON = 1e-9
const val REGRESSION_RSQUARED_EPSILON = 1e-9
const val REGRESSION_R2_WEIGHT = 0.7
const val REGRESSION_COMPLETENESS_WEIGHT = 0.3
const val MAD_MIN_EPSILON = 1e-9

/** Population-gradient default for within-session e1RM decay per set index (Q2 resolution). */
const val POPULATION_FATIGUE_DECAY_SLOPE: Double = -2.0

/** Intercept for the population-gradient fatigue default (Q2 resolution). */
const val POPULATION_FATIGUE_DECAY_INTERCEPT: Double = 0.0
