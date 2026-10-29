package com.stratum.domain.workout.fatigue

import kotlin.math.abs
import kotlin.math.round
import com.stratum.domain.program.generation.prescription.LAST_SET_FLOOR
import com.stratum.domain.program.generation.prescription.REP_RANGE_CEILING_CAP
import com.stratum.domain.workout.BACKOFF_REP_FLOOR

/**
 * Absolute last-set floor (reps). Aligns with
 * [com.stratum.domain.workout.BACKOFF_REP_FLOOR] and generation
 * [com.stratum.domain.program.generation.prescription.LAST_SET_FLOOR].
 *
 * If the lifter only hits the bottom of the top-set band, every remaining set must
 * still clear this many reps after within-session fatigue.
 */
const val LAST_SET_REP_FLOOR: Int = 5

/**
 * Caps proposed / generation-derived rep ceilings so extreme slopes cannot produce
 * absurd bands. Matches [com.stratum.domain.program.generation.prescription.REP_RANGE_CEILING_CAP].
 */
const val REP_RANGE_CEILING: Int = 30

/**
 * Expected rep drop from set 1 → last set given a linear per-set fatigue slope
 * (reps/set, typically negative). Zero when [setCount] ≤ 1.
 */
fun fatigueDropReps(slope: Double, setCount: Int): Int {
    if (setCount <= 1) return 0
    return round(abs(slope) * (setCount - 1)).toInt()
}

/**
 * Minimum top-set [repMin] so performing only the bottom of the band still leaves
 * ≥ [lastSetFloor] reps on the last set after fatigue.
 *
 * Returns `null` when [setCount] ≤ 1 (no multi-set fatigue signal).
 */
fun minimumTopRepMinForLastSetFloor(
    slope: Double,
    setCount: Int,
    lastSetFloor: Int = LAST_SET_REP_FLOOR,
): Int? {
    if (setCount <= 1) return null
    return lastSetFloor + fatigueDropReps(slope, setCount)
}
