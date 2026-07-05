package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ProgramWorkoutMergeTest {
    @Test
    @DisplayName("mergeWorkout replaces matching workout and preserves others")
    fun mergeWorkoutReplacesTargetOnly() {
        val program = sampleProgram()
        val updated =
            program.workouts.first().copy(
                name = "Updated Push",
                workoutLifts =
                    listOf(
                        program.workouts.first().workoutLifts.first().copy(
                            liftId = LiftId("incline-db-bench"),
                        ),
                    ),
            )

        val merged = program.mergeWorkout(WorkoutId("w-1"), updated)

        assertEquals("Updated Push", merged.workouts[0].name)
        assertEquals(LiftId("incline-db-bench"), merged.workouts[0].workoutLifts[0].liftId)
        assertEquals("Pull", merged.workouts[1].name)
        assertEquals(ProgramId("p-1"), merged.workouts[0].programId)
    }

    @Test
    @DisplayName("mergeWorkout throws when workout id is missing")
    fun mergeWorkoutThrowsWhenMissing() {
        val program = sampleProgram()

        assertThrows<IllegalArgumentException> {
            program.mergeWorkout(
                WorkoutId("missing"),
                program.workouts.first().copy(id = WorkoutId("missing")),
            )
        }
    }

    private fun sampleProgram(): Program =
        Program(
            id = ProgramId("p-1"),
            name = "Split",
            createdAt = Instant.parse("2026-06-26T12:00:00Z"),
            cycleLength = 2,
            trainingWeekdays = setOf(Weekday.Monday, Weekday.Wednesday),
            workouts =
                listOf(
                    Workout(
                        id = WorkoutId("w-1"),
                        programId = ProgramId("p-1"),
                        name = "Push",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = WorkoutLiftId("wl-1"),
                                    workoutId = WorkoutId("w-1"),
                                    liftId = LiftId("bench-press"),
                                    progressionScheme = ProgressionScheme.Linear,
                                    position = 0,
                                    sets = emptyList(),
                                ),
                            ),
                    ),
                    Workout(
                        id = WorkoutId("w-2"),
                        programId = ProgramId("p-1"),
                        name = "Pull",
                        weekday = Weekday.Wednesday,
                        position = 1,
                        workoutLifts = emptyList(),
                    ),
                ),
        )
}
