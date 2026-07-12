package com.stratum.domain.workout

import com.stratum.domain.workout.fatigue.LAST_SET_REP_FLOOR
import com.stratum.domain.workout.fatigue.REP_RANGE_CEILING
import com.stratum.domain.workout.fatigue.fatigueDropReps
import com.stratum.domain.workout.fatigue.minimumTopRepMinForLastSetFloor

/**
 * Pure inputs for evaluating whether a template lift's rep band should shift right.
 *
 * [fatigueSlope] must be a **measured** per-lift slope — never the population default.
 * Null slope → no proposal (prefer silence over population spam).
 */
data class RepRangeShiftInput(
    val fatigueSlope: Double?,
    val setCount: Int,
    val currentRepMin: Int,
    val currentRepMax: Int,
    val lastSetFloor: Int = LAST_SET_REP_FLOOR,
    val ceiling: Int = REP_RANGE_CEILING,
)

/**
 * Pure evaluation result. Null when no shift is warranted.
 */
data class RepRangeShiftEvaluation(
    val proposedRepMin: Int,
    val proposedRepMax: Int,
    val fatigueDrop: Int,
    val lastSetShortfall: Int,
)

/**
 * Decides whether the current band is too low for the lift's within-session fatigue.
 *
 * Rule: if the lifter only hits [RepRangeShiftInput.currentRepMin] on the top set, projected
 * last-set reps after fatigue must stay ≥ [RepRangeShiftInput.lastSetFloor]. When they would
 * fall short, raise the band floor to [minimumTopRepMinForLastSetFloor] and **preserve width**
 * (including zero width for fixed-rep prescriptions).
 *
 * Does **not** apply the generation hypertrophic floor (8). Proposed max is capped at [ceiling].
 */
fun evaluateRepRangeShift(input: RepRangeShiftInput): RepRangeShiftEvaluation? {
    val slope = input.fatigueSlope
    val band = resolvedBand(input.currentRepMin, input.currentRepMax)
    if (slope == null || input.setCount <= 1 || band == null) {
        return null
    }

    val fatigueDrop = fatigueDropReps(slope, input.setCount)
    val projectedLast = band.first - fatigueDrop
    if (projectedLast >= input.lastSetFloor) {
        return null
    }

    return proposeRaisedBand(
        slope = slope,
        setCount = input.setCount,
        band = band,
        lastSetFloor = input.lastSetFloor,
        ceiling = input.ceiling,
        fatigueDrop = fatigueDrop,
        projectedLast = projectedLast,
    )
}

private fun proposeRaisedBand(
    slope: Double,
    setCount: Int,
    band: Pair<Int, Int>,
    lastSetFloor: Int,
    ceiling: Int,
    fatigueDrop: Int,
    projectedLast: Int,
): RepRangeShiftEvaluation? {
    val proposedMin =
        minimumTopRepMinForLastSetFloor(slope, setCount, lastSetFloor)
            ?: return null
    if (proposedMin <= band.first || proposedMin > ceiling) {
        return null
    }

    val width = (band.second - band.first).coerceAtLeast(0)
    val proposedMax = (proposedMin + width).coerceAtMost(ceiling)
    if (proposedMax < proposedMin) {
        return null
    }
    if (proposedMin <= band.first && proposedMax <= band.second) {
        return null
    }

    return RepRangeShiftEvaluation(
        proposedRepMin = proposedMin,
        proposedRepMax = proposedMax,
        fatigueDrop = fatigueDrop,
        lastSetShortfall = (lastSetFloor - projectedLast).coerceAtLeast(1),
    )
}

/** Positive (min, max) band, or null when empty / unusable. */
private fun resolvedBand(repMin: Int, repMax: Int): Pair<Int, Int>? {
    if (repMin <= 0 && repMax <= 0) return null
    val min = repMin.coerceAtLeast(0)
    val max =
        when {
            repMax <= 0 -> min
            else -> repMax.coerceAtLeast(min)
        }
    return if (min > 0) min to max else null
}
