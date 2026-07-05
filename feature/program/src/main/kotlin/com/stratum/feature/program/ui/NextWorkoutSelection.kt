package com.stratum.feature.program.ui

/**
 * Returns the workout id of the first incomplete non-rest-day entry in weekly schedule order,
 * or null when every workout is marked complete.
 */
internal fun findNextWorkoutId(scheduleItems: List<ScheduleItemState>): String? =
    scheduleItems
        .filterIsInstance<ScheduleItemState.Workout>()
        .firstOrNull { item -> !item.workout.isCompleted }
        ?.workout
        ?.workoutId
