package com.stratum.feature.program.ui

/**
 * Returns the workout id of the first incomplete, lift-bearing non-rest-day entry in weekly
 * schedule order, or null when no such workout remains.
 *
 * Rest days are excluded by type. Empty-lift workout cards are skipped so expand / start-next
 * target a real training day even when an incomplete empty day precedes it.
 */
internal fun findNextWorkoutId(scheduleItems: List<ScheduleItemState>): String? =
    scheduleItems
        .filterIsInstance<ScheduleItemState.Workout>()
        .firstOrNull { item ->
            !item.workout.isCompleted && item.workout.lifts.isNotEmpty()
        }
        ?.workout
        ?.workoutId
