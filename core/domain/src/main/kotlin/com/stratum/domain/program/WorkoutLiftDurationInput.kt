package com.stratum.domain.program

/**
 * Per-lift duration input for [EstimateWorkoutDurationMinutesUseCase].
 * When [supersetGroupId] is set, [position] orders lifts within the group chain.
 *
 * [repsBySet] is parallel to [restSecondsBySet] (estimated working reps per set).
 * Empty or short lists fall back to a default mid-band of 10 reps.
 */
data class WorkoutLiftDurationInput(
    val restSecondsBySet: List<Int>,
    val repsBySet: List<Int> = emptyList(),
    val position: Int = 0,
    val supersetGroupId: String? = null,
    val useRestTimerBetweenLifts: Boolean = false,
)
