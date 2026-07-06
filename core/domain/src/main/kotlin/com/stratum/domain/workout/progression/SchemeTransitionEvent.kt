package com.stratum.domain.workout.progression

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutLiftId

/**
 * Auto-transition event (D8). Emitted when a Linear lift exhausts
 * [LinearProgressionConfig.resetsBeforeTransition]. D8 locks the transition to
 * Automatic — the snapshot applies it (mutates the lift's `progressionScheme`
 * in-memory and appends a `ProgramChangeEvent`); no UI prompt.
 */
data class SchemeTransitionEvent(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val from: ProgressionScheme,
    val to: ProgressionScheme,
    val reason: SchemeTransitionReason,
)

enum class SchemeTransitionReason { ResetCyclesExhausted }

/**
 * Per-lift stall/reset/transition state derived from history (D8).
 *
 * [emittedEvent] is non-null iff [shouldTransitionToDouble] is true: it carries the
 * `SchemeTransitionEvent` (from Linear → Double, reason ResetCyclesExhausted) the
 * engine surfaced from history. Asserted in `S4-linear-2-resets-transition-to-double`.
 */
data class LinearProgressionState(
    val liftId: LiftId,
    val currentStallCount: Int,
    val currentResetCount: Int,
    val shouldReset: Boolean,
    val shouldTransitionToDouble: Boolean,
    val emittedEvent: SchemeTransitionEvent? = null,
)
