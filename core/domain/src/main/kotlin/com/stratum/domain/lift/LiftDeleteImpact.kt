package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId

/** One workout whose template slots point at the lift being deleted. */
data class AffectedLiftWorkout(
    val programName: String,
    val workoutName: String,
    val slotCount: Int,
)

/**
 * Delete-impact preview for one lift (STR-131 Scope 3): computed from the
 * database at dialog open, not from UI state, so the numbers are true even
 * when the on-screen catalog list is filtered or searched.
 *
 * - [affectedWorkouts]: template slots that will be removed (the workouts
 *   themselves survive, possibly empty).
 * - [loggedSetCount]: logged sets linked to the lift. They stay in history,
 *   untouched, still resolving the retained tombstoned row by id.
 * - [inActiveSession]: the lift is part of the in-progress session (template
 *   slot or already-logged set). The delete proceeds anyway — the session
 *   grandfathers the retained row; the dialog only notes it.
 */
data class LiftDeleteImpact(
    val liftId: LiftId,
    val liftName: String,
    val affectedWorkouts: List<AffectedLiftWorkout>,
    val loggedSetCount: Int,
    val inActiveSession: Boolean,
) {
    val totalSlotCount: Int get() = affectedWorkouts.sumOf { it.slotCount }
}

