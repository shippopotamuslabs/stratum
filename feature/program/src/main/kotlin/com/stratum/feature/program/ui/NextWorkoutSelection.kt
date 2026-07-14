package com.stratum.feature.program.ui

/**
 * Returns the workout id of the first incomplete, lift-bearing non-rest-day entry in **cycle
 * slot order** (Programs strip), or null when no such workout remains.
 *
 * Rest days are excluded by type. Empty-lift workout cards are skipped so start-next
 * targets a real training day even when an incomplete empty day precedes it.
 * Card expand state is independent (last-opened, DataStore-backed).
 */
internal fun findNextWorkoutId(scheduleItems: List<ScheduleItemState>): String? =
    scheduleItems
        .filterIsInstance<ScheduleItemState.Workout>()
        .firstOrNull { item ->
            !item.workout.isCompleted && item.workout.lifts.isNotEmpty()
        }
        ?.workout
        ?.workoutId
