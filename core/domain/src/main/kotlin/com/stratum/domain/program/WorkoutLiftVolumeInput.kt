package com.stratum.domain.program

/** Lift identity and set count for per-workout muscle volume math. */
data class WorkoutLiftVolumeInput(
    val liftId: String,
    val setCount: Int,
)
