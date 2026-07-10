package com.stratum.domain.workout.progression

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId

/**
 * Stall strictness. Determines how a sub-range session counts toward the stall
 * counter that gates Linear → Double auto-transition.
 */
enum class StallStrictness {
    /** Stall = top-set RIR met but bottom of rep range missed (recommended). */
    RirBased,

    /** Stall = could not complete bottom of range at any RIR. */
    AbsoluteFailure,
}

/**
 * Tuning knobs for [LinearProgressionEngine].
 *
 * Auto-transition is locked: after [stallThresholdSessions] consecutive stalls on a
 * lift, the snapshot flips the scheme to Double. There is no 10% reset / rebuild
 * cycle — modern coaching treats repeated LP failure as a cue to change the
 * progression *model*, not to churn deloads on the same model.
 */
data class LinearProgressionConfig(
    val lowerBodyIncrement: Weight,
    val upperBodyIncrement: Weight,
    val minimumIncrement: Weight,
    val perLiftIncrementOverride: Map<LiftId, Weight>,
    /** Consecutive stall sessions required before Linear → Double (default 2). */
    val stallThresholdSessions: Int,
    val stallStrictness: StallStrictness,
)
