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
    @DisplayName("effectiveTrainingWeekdays prefers bound workout weekdays, falling back to metadata only when none are bound")
    fun effectiveTrainingWeekdays_prefersBoundWorkouts() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 2,
                trainingWeekdays = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday),
                workouts =
                    listOf(
                        workout("w-0", 0).copy(weekday = Weekday.Wednesday),
                        workout("w-1", 1).copy(weekday = Weekday.Friday),
                    ),
            )

        // Workout.weekday is the single source of truth; metadata is a defensive fallback only.
        assertEquals(setOf(Weekday.Wednesday, Weekday.Friday), program.effectiveTrainingWeekdays())
        val schedule = buildWeeklySchedule(program)
        assertEquals(2, schedule.filterIsInstance<ScheduleDayItem.WorkoutDay>().size)
    }

    @Test
    @DisplayName("effectiveTrainingWeekdays falls back to trainingWeekdays metadata when no workout carries a weekday")
    fun effectiveTrainingWeekdays_fallsBackToMetadataWhenUnbound() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 2,
                trainingWeekdays = setOf(Weekday.Monday, Weekday.Thursday),
                workouts = listOf(workout("w-0", 0), workout("w-1", 1)),
            )

        assertEquals(setOf(Weekday.Monday, Weekday.Thursday), program.effectiveTrainingWeekdays())
    }

    @Test
    @DisplayName("withReconciledTrainingWeekdays re-derives the cache after workouts are dropped")
    fun withReconciledTrainingWeekdays_reDerivesAfterDroppingWorkouts() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 3,
                trainingWeekdays = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday),
                workouts =
                    listOf(
                        workout("w-0", 0).copy(weekday = Weekday.Monday),
                        workout("w-1", 1).copy(weekday = Weekday.Wednesday),
                        workout("w-2", 2).copy(weekday = Weekday.Friday),
                    ),
            )

        // Simulate sanitization dropping the Friday workout.
        val pruned = program.copy(workouts = program.workouts.take(2)).withReconciledTrainingWeekdays()

        assertEquals(setOf(Weekday.Monday, Weekday.Wednesday), pruned.trainingWeekdays)
        assertEquals(pruned.workouts.mapNotNull { it.weekday }.toSet(), pruned.trainingWeekdays)
    }

    @Test
    @DisplayName("withReconciledTrainingWeekdays clears stale metadata when no workout is bound")
    fun withReconciledTrainingWeekdays_clearsStaleMetadataWhenUnbound() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 2,
                trainingWeekdays = setOf(Weekday.Monday, Weekday.Wednesday),
                workouts = listOf(workout("w-0", 0), workout("w-1", 1)),
            )

        val reconciled = program.withReconciledTrainingWeekdays()

        assertTrue(reconciled.trainingWeekdays.isEmpty())
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
