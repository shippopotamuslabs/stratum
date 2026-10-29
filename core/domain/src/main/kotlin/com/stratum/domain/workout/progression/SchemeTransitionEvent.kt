package com.stratum.domain.workout.progression

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent

/**
 * Auto-transition event. Emitted when a Linear lift hits
 * [LinearProgressionConfig.stallThresholdSessions] consecutive stalls. The snapshot
 * applies it automatically (in-memory scheme flip + [ProgramChangeEvent]); no UI prompt.
 */
data class SchemeTransitionEvent(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val from: ProgressionScheme,
    val to: ProgressionScheme,
    val reason: SchemeTransitionReason,
)

enum class SchemeTransitionReason {
    /** [LinearProgressionConfig.stallThresholdSessions] consecutive stalls — LP exhausted. */
    ConsecutiveStalls,
}

/**
 * Per-lift stall/transition state derived from history.
 *
 * [emittedEvent] is non-null iff [shouldTransitionToDouble] is true.
 */
data class LinearProgressionState(
    val liftId: LiftId,
    val currentStallCount: Int,
    val shouldTransitionToDouble: Boolean,
    val emittedEvent: SchemeTransitionEvent? = null,
)
