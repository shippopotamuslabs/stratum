package com.stratum.domain.program

import com.stratum.domain.lift.Lift

/** Lift identity and set count for per-workout muscle volume math. */
data class WorkoutLiftVolumeInput(
    val liftId: String,
    val setCount: Int,
)
