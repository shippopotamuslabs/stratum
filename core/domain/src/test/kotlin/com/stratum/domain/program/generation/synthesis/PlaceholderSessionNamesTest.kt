package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaceholderSessionNamesTest {
    @Test
    fun `relabels placeholder sessions by weekday not cycle position`() {
        val workouts =
            listOf(
                ProgramWorkoutDraft(name = "Session 1", weekday = Weekday.Monday.name, position = 0),
                ProgramWorkoutDraft(name = "Session 5", weekday = Weekday.Tuesday.name, position = 4),
                ProgramWorkoutDraft(name = "Session 4", weekday = Weekday.Wednesday.name, position = 3),
                ProgramWorkoutDraft(name = "Session 2", weekday = Weekday.Thursday.name, position = 1),
                ProgramWorkoutDraft(name = "Session 3", weekday = Weekday.Friday.name, position = 2),
            )

        val relabeled = relabelPlaceholderSessionNamesByWeekday(workouts)
        val namesInCalendarOrder =
            relabeled
                .sortedBy { workout -> Weekday.valueOf(workout.weekday!!).ordinal }
                .map { workout -> workout.name }

        assertEquals(
            listOf("Session 1", "Session 2", "Session 3", "Session 4", "Session 5"),
            namesInCalendarOrder,
        )
    }

    @Test
    fun `leaves non-placeholder workout names unchanged`() {
        val workouts =
            listOf(
                ProgramWorkoutDraft(name = "Lower A", weekday = Weekday.Monday.name, position = 0),
                ProgramWorkoutDraft(name = "Upper B", weekday = Weekday.Wednesday.name, position = 1),
            )

        val relabeled = relabelPlaceholderSessionNamesByWeekday(workouts)

        assertEquals(workouts, relabeled)
    }
}
