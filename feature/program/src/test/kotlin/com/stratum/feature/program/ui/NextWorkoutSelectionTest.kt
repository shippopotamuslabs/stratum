package com.stratum.feature.program.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class NextWorkoutSelectionTest {
    @Test
    @DisplayName("findNextWorkoutId skips rest days and completed workouts")
    fun findNextWorkoutId_skipsRestDaysAndCompletedWorkouts() {
        val schedule =
            listOf(
                ScheduleItemState.Workout(
                    workout =
                        WorkoutViewState(
                            workoutId = "w-done",
                            name = "Done Day",
                            weekdayLabel = "Monday",
                            position = 0,
                            lifts = emptyList(),
                            isCompleted = true,
                        ),
                ),
                ScheduleItemState.RestDay(weekdayLabel = "Tuesday"),
                ScheduleItemState.Workout(
                    workout =
                        WorkoutViewState(
                            workoutId = "w-next",
                            name = "Next Day",
                            weekdayLabel = "Wednesday",
                            position = 1,
                            lifts = emptyList(),
                        ),
                ),
            )

        assertEquals("w-next", findNextWorkoutId(schedule))
    }

    @Test
    @DisplayName("findNextWorkoutId returns null when all workouts are complete")
    fun findNextWorkoutId_returnsNullWhenAllComplete() {
        val schedule =
            listOf(
                ScheduleItemState.Workout(
                    workout =
                        WorkoutViewState(
                            workoutId = "w-1",
                            name = "Day A",
                            weekdayLabel = "Monday",
                            position = 0,
                            lifts = emptyList(),
                            isCompleted = true,
                        ),
                ),
            )

        assertNull(findNextWorkoutId(schedule))
    }
}
