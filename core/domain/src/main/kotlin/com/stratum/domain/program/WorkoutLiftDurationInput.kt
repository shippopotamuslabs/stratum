package com.stratum.domain.program

/** Rest durations per set in lift order for workout time estimation. */
data class WorkoutLiftDurationInput(
    val restSecondsBySet: List<Int>,
)
