package com.stratum.domain.workout.bodyweight

import com.stratum.domain.response.POPULATION_FATIGUE_DECAY_INTERCEPT
import com.stratum.domain.response.POPULATION_FATIGUE_DECAY_SLOPE
import com.stratum.domain.workout.fatigue.projectReps
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Load-free rep projection for bodyweight straight sets (STR-29).
 *
 * The weighted pipeline anchors capacity on e1RM-from-load
 * ([com.stratum.domain.training.EstimatedOneRm.calculate]) and converts back via
 * [projectReps]. Bodyweight has no measurable load (stored as `Weight(0.0, ...)`),
 * so capacity anchors directly on performed failure-reps (reps + RIR) and decays by
 * the per-lift rep slope across set indices — the same linear-decay story as
 * [projectReps] minus the e1RM round-trip (which is ~identity when load is constant).
 *
 * Weight recommendations stay suppressed for bodyweight (always 0); only reps are
 * projected here.
 */
object BodyweightRepProjection {
    /**
     * DESIGN CALL (STR-29 — reviewer decision): when the per-lift fatigue slope is
     * present but effectively flat, observed decay is zero and projections would stay
     * flat across the block. When true, fall back to [POPULATION_FATIGUE_DECAY_SLOPE]
     * so bodyweight rep targets still decay across sets; when false, honor the flat
     * per-lift slope as measured.
     *
     * Note: the insights pipeline already nulls flat slopes upstream
     * ([com.stratum.domain.importing.TrainingInsightsExtractor] discards |slope| < 0.5
     * as RIR noise), so a ~0 slope normally arrives as null and takes the population
     * fallback through the null path below. This guard is a backstop for any other
     * source that might supply a near-zero slope directly.
     */
    const val USE_POPULATION_SLOPE_WHEN_PER_LIFT_SLOPE_FLAT: Boolean = true

    /**
     * |slope| at or below this counts as "flat" (no observable decay). Mirrors the
     * upstream noise floor (MIN_FATIGUE_SLOPE_MAGNITUDE = 0.5).
     */
    const val FLAT_SLOPE_MAGNITUDE_THRESHOLD: Double = 0.5

    const val MIN_REPS: Int = 1

    /**
     * Failure-rep capacity (RIR 0) for the set at 1-based [setIndex]
     * (the performed anchor set is index 1).
     */
    fun expectedRepsAtRir0(
        performedReps: Int,
        performedRir: Int?,
        setIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int {
        val baselineReps = (performedReps + (performedRir ?: 0)).coerceAtLeast(1)
        val slope = resolveSlope(perLiftSlope).coerceAtMost(0.0)
        val intercept = (perLiftIntercept ?: POPULATION_FATIGUE_DECAY_INTERCEPT).coerceAtMost(0.0)
        val decay = slope * (setIndex - 1) + intercept
        val projected = baselineReps + decay.coerceAtMost(0.0)
        return projected.coerceAtLeast(MIN_REPS.toDouble()).roundToInt()
    }

    /** Slot recommendation: failure capacity minus [rirGoal]. */
    fun expectedReps(
        performedReps: Int,
        performedRir: Int?,
        setIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
        rirGoal: Int = 0,
    ): Int {
        val atZero =
            expectedRepsAtRir0(
                performedReps = performedReps,
                performedRir = performedRir,
                setIndex = setIndex,
                perLiftSlope = perLiftSlope,
                perLiftIntercept = perLiftIntercept,
            )
        return (atZero - rirGoal.coerceAtLeast(0)).coerceAtLeast(MIN_REPS)
    }

    private fun resolveSlope(perLiftSlope: Double?): Double {
        if (perLiftSlope == null) return POPULATION_FATIGUE_DECAY_SLOPE
        if (USE_POPULATION_SLOPE_WHEN_PER_LIFT_SLOPE_FLAT &&
            abs(perLiftSlope) <= FLAT_SLOPE_MAGNITUDE_THRESHOLD
        ) {
            return POPULATION_FATIGUE_DECAY_SLOPE
        }
        return perLiftSlope
    }
}
