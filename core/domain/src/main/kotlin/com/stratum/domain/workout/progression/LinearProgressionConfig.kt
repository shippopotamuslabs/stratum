package com.stratum.domain.workout.progression

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId

/**
 * Stall strictness (Q1a). Determines how a sub-range session counts toward the stall
 * counter.
 */
enum class StallStrictness {
    /** Stall = top-set RIR met but bottom of rep range missed (recommended). */
    RirBased,

    /** Stall = could not complete bottom of range at any RIR. */
    AbsoluteFailure,
}

/**
 * Remaining Q1a tuning knobs (D8). `transitionMode` is intentionally absent — D8 locks
 * auto-transition after `resetsBeforeTransition` resets (no user confirm).
 */
data class LinearProgressionConfig(
    val lowerBodyIncrement: Weight,
    val upperBodyIncrement: Weight,
    val minimumIncrement: Weight,
    val perLiftIncrementOverride: Map<LiftId, Weight>,
    val stallThresholdSessions: Int,
    val resetFraction: Double,
    val perLiftResetFractionOverride: Map<LiftId, Double>,
    val resetsBeforeTransition: Int,
    val stallStrictness: StallStrictness,
)
