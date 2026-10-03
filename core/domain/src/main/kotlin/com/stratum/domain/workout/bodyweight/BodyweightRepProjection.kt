package com.stratum.domain.workout.bodyweight

import com.stratum.domain.workout.fatigue.applyFatigueDecay

/**
 * Load-free rep projection for bodyweight straight sets (STR-29).
 *
 * The weighted pipeline anchors capacity on e1RM-from-load
 * ([com.stratum.domain.training.EstimatedOneRm.calculate]) and converts back via
 * [com.stratum.domain.workout.fatigue.projectReps]. Bodyweight has no measurable load
 * (stored as `Weight(0.0, ...)`), so capacity anchors directly on performed
 * failure-reps (reps + RIR) and decays by the per-lift rep slope across set indices
 * via the shared [applyFatigueDecay] core — the same linear-decay story as
 * `projectReps` minus the e1RM round-trip (which is ~identity when load is constant).
 *
 * When the per-lift slope is absent, the population slope (-2.0 reps/set) applies.
 * The insights pipeline already nulls effectively-flat slopes upstream
 * ([com.stratum.domain.importing.TrainingInsightsExtractor] discards |slope| < 0.5
 * as RIR noise), so a flat measurement arrives as null and takes the population
 * fallback through the null path — no separate flat-slope branch is needed here.
 *
 * Weight recommendations stay suppressed for bodyweight (always 0); only reps are
 * projected here.
 */
object BodyweightRepProjection {
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
        val baselineReps = (performedReps + (performedRir ?: 0)).coerceAtLeast(1).toDouble()
        return applyFatigueDecay(
            baselineReps = baselineReps,
            setIndex = setIndex,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
            minReps = MIN_REPS.toDouble(),
        )
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
}
