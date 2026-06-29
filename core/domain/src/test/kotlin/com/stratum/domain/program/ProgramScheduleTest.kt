package com.stratum.domain.program

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ProgramScheduleTest {
    @Test
    @DisplayName("assignProgramSchedule binds workouts to sorted training weekdays")
    fun assignProgramSchedule_bindsWorkoutsToWeekdays() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 3,
                workouts =
                    listOf(
                        workout("w-0", 0),
                        workout("w-1", 1),
                        workout("w-2", 2),
                    ),
            )

        val scheduled =
            assignProgramSchedule(
                program = program,
                trainingWeekdays = listOf(Weekday.Wednesday, Weekday.Monday, Weekday.Friday),
            )

        assertEquals(setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday), scheduled.trainingWeekdays)
        assertEquals(Weekday.Monday, scheduled.workouts.first { it.position == 0 }.weekday)
        assertEquals(Weekday.Wednesday, scheduled.workouts.first { it.position == 1 }.weekday)
        assertEquals(Weekday.Friday, scheduled.workouts.first { it.position == 2 }.weekday)
    }

    @Test
    @DisplayName("buildWeeklySchedule inserts rest days for non-training weekdays")
    fun buildWeeklySchedule_insertsRestDays() {
        val program =
            assignProgramSchedule(
                program =
                    Program(
                        id = ProgramId("p-1"),
                        name = "Test",
                        createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                        cycleLength = 2,
                        workouts = listOf(workout("w-0", 0), workout("w-1", 1)),
                    ),
                trainingWeekdays = listOf(Weekday.Monday, Weekday.Thursday),
            )

        val schedule = buildWeeklySchedule(program)

        assertEquals(7, schedule.size)
        assertTrue(schedule[0] is ScheduleDayItem.WorkoutDay)
        assertTrue(schedule[1] is ScheduleDayItem.RestDay)
        assertEquals(Weekday.Tuesday, (schedule[1] as ScheduleDayItem.RestDay).weekday)
    }

    @Test
    @DisplayName("assignProgramSchedule rejects workout count mismatch")
    fun assignProgramSchedule_rejectsMismatch() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 1,
                workouts = listOf(workout("w-0", 0)),
            )

        assertThrows<IllegalArgumentException> {
            assignProgramSchedule(program, listOf(Weekday.Monday, Weekday.Wednesday))
        }
    }

    private fun workout(id: String, position: Int) =
        Workout(
            id = WorkoutId(id),
            programId = ProgramId("p-1"),
            name = "Session $position",
            weekday = null,
            position = position,
            workoutLifts = emptyList(),
        )
}
