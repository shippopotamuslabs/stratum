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

    @Test
    @DisplayName("buildWeeklyScheduleOrFallback shows unbound workouts instead of seven rest days")
    fun buildWeeklyScheduleOrFallback_unboundWorkoutsNotAllRest() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Scratch",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 2,
                trainingWeekdays = emptySet(),
                workouts =
                    listOf(
                        workout("w-0", 0),
                        workout("w-1", 1),
                    ),
            )

        val outcome = buildWeeklyScheduleOrFallback(program)

        assertEquals(2, outcome.items.size)
        assertTrue(outcome.items.all { item -> item is ScheduleDayItem.WorkoutDay })
        assertEquals(
            listOf("w-0", "w-1"),
            outcome.items.filterIsInstance<ScheduleDayItem.WorkoutDay>().map { item -> item.workout.id.value },
        )
        // Fallback items are byte-for-byte the positional fallback (Monday placeholder).
        assertEquals(
            program.workouts.map { workout ->
                ScheduleDayItem.WorkoutDay(weekday = Weekday.Monday, workout = workout)
            },
            outcome.items,
        )
        // No weekday-bound workouts and no metadata: path (a) diagnostic.
        assertEquals(
            listOf(
                ScheduleDiagnostic.FallbackNoWeekdayBoundWorkouts,
            ),
            outcome.diagnostics,
        )
        // Strict builder still produces all rest for empty training (invariant for active UI).
        val strict = buildWeeklySchedule(program)
        assertEquals(7, strict.size)
        assertTrue(strict.all { item -> item is ScheduleDayItem.RestDay })
    }

    @Test
    @DisplayName("buildWeeklyScheduleOrFallback carries the strict failure cause when bindings are stale")
    fun buildWeeklyScheduleOrFallback_strictFailureCarriesCause() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Stale",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 2,
                // Stale metadata: no workout carries a weekday, so the strict builder throws
                // "Missing workout for training weekday Monday".
                trainingWeekdays = setOf(Weekday.Monday),
                workouts =
                    listOf(
                        workout("w-0", 0),
                        workout("w-1", 1),
                    ),
            )

        val outcome = buildWeeklyScheduleOrFallback(program)

        // Fallback items are byte-for-byte the positional fallback (Monday placeholder).
        assertEquals(
            program.workouts.map { workout ->
                ScheduleDayItem.WorkoutDay(weekday = Weekday.Monday, workout = workout)
            },
            outcome.items,
        )
        // Strict builder threw: path (b) diagnostic carrying the cause.
        assertEquals(1, outcome.diagnostics.size)
        val diagnostic = outcome.diagnostics.single()
        assertTrue(diagnostic is ScheduleDiagnostic.FallbackStrictBuildFailed)
        val cause = (diagnostic as ScheduleDiagnostic.FallbackStrictBuildFailed).cause
        assertTrue(cause is IllegalStateException)
        assertTrue(cause.message?.contains("Missing workout for training weekday Monday") == true)
    }

    @Test
    @DisplayName("buildWeeklyScheduleOrFallback returns the real schedule with zero diagnostics")
    fun buildWeeklyScheduleOrFallback_realScheduleHasNoDiagnostics() {
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

        val outcome = buildWeeklyScheduleOrFallback(program)

        assertEquals(buildWeeklySchedule(program), outcome.items)
        assertTrue(outcome.diagnostics.isEmpty())
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
