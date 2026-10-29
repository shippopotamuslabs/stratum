package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.fatigue.LAST_SET_REP_FLOOR

/**
 * Max rep-range shift cards per completed session (product decision; matches equipment cap).
 */
const val MAX_SESSION_REP_RANGE_SHIFT_PROPOSALS: Int = 3

/**
 * Soft cap for dismissed fingerprints retained in DataStore (oldest dropped first).
 */
const val MAX_DISMISSED_REP_RANGE_SHIFT_FINGERPRINTS: Int = 100

/**
 * A pre-filled post-workout suggestion to shift a lift's prescribed rep band right so the
 * last set stays ≥ [com.stratum.domain.workout.fatigue.LAST_SET_REP_FLOOR] when the top set
 * only hits the bottom of the range.
 *
 * [id] is a stable fingerprint for DataStore deferral — not a display string.
 * [liftName] is snapshotted at analysis time so presentation does not re-load the catalog.
 */
data class RepRangeShiftProposal(
    val id: String,
    val workoutLiftId: WorkoutLiftId,
    val workoutId: WorkoutId,
    val liftId: LiftId,
    val liftName: String,
    val currentRepMin: Int,
    val currentRepMax: Int,
    val proposedRepMin: Int,
    val proposedRepMax: Int,
    val setCount: Int,
    /** Measured per-lift fatigue slope (reps/set) that drove the proposal. */
    val fatigueSlope: Double,
    /**
     * How many reps the last set would fall short of the floor at the current band min.
     * Used for ranking (larger shortfall = higher priority).
     */
    val lastSetShortfall: Int,
)

/**
 * Stable fingerprint for dismiss / re-show logic.
 *
 * Includes the current and proposed bands so a later, steeper slope (new proposed band)
 * can re-surface after a prior dismiss of a milder suggestion.
 */
fun repRangeShiftProposalFingerprint(
    workoutLiftId: WorkoutLiftId,
    currentRepMin: Int,
    currentRepMax: Int,
    proposedRepMin: Int,
    proposedRepMax: Int,
): String =
    "rrs:${workoutLiftId.value}:$currentRepMin-$currentRepMax->$proposedRepMin-$proposedRepMax"
