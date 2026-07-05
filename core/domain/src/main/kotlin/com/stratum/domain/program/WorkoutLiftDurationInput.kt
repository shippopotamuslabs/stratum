package com.stratum.domain.program

/**
 * Per-lift duration input for [EstimateWorkoutDurationMinutesUseCase].
 * When [supersetGroupId] is set, [position] orders lifts within the group chain.
 */
data class WorkoutLiftDurationInput(
    val restSecondsBySet: List<Int>,
    val position: Int = 0,
    val supersetGroupId: String? = null,
    val useRestTimerBetweenLifts: Boolean = false,
)
