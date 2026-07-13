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
                            lifts = listOf(sampleLift("wl-done")),
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
                            lifts = listOf(sampleLift("wl-next")),
                        ),
                ),
            )

        assertEquals("w-next", findNextWorkoutId(schedule))
    }

    @Test
    @DisplayName("findNextWorkoutId skips incomplete empty-lift workouts")
    fun findNextWorkoutId_skipsEmptyLiftWorkouts() {
        val schedule =
            listOf(
                ScheduleItemState.RestDay(weekdayLabel = "Monday"),
                ScheduleItemState.Workout(
                    workout =
                        WorkoutViewState(
                            workoutId = "w-empty",
                            name = "Empty Day",
                            weekdayLabel = "Tuesday",
                            position = 0,
                            lifts = emptyList(),
                        ),
                ),
                ScheduleItemState.Workout(
                    workout =
                        WorkoutViewState(
                            workoutId = "w-lifts",
                            name = "Training Day",
                            weekdayLabel = "Wednesday",
                            position = 1,
                            lifts = listOf(sampleLift("wl-1")),
                        ),
                ),
            )

        assertEquals("w-lifts", findNextWorkoutId(schedule))
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
                            lifts = listOf(sampleLift("wl-1")),
                            isCompleted = true,
                        ),
                ),
            )

        assertNull(findNextWorkoutId(schedule))
    }

    @Test
    @DisplayName("findNextWorkoutId returns null when only empty incomplete workouts remain")
    fun findNextWorkoutId_returnsNullWhenOnlyEmptyRemain() {
        val schedule =
            listOf(
                ScheduleItemState.RestDay(weekdayLabel = "Monday"),
                ScheduleItemState.Workout(
                    workout =
                        WorkoutViewState(
                            workoutId = "w-empty",
                            name = "Empty",
                            weekdayLabel = "Tuesday",
                            position = 0,
                            lifts = emptyList(),
                        ),
                ),
            )

        assertNull(findNextWorkoutId(schedule))
    }

    private fun sampleLift(workoutLiftId: String): WorkoutLiftLineState =
        WorkoutLiftLineState(
            workoutLiftId = workoutLiftId,
            liftId = "lift-$workoutLiftId",
            name = "Lift $workoutLiftId",
            setCount = 3,
            repRange = "8–10",
            topSetRpe = "",
            position = 0,
        )
}
