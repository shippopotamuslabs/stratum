package com.stratum.domain.training

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.program.Workout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CycleProgressResolverTest {
    private val program = WorkoutLoggingFixtures.twoWorkoutProgram()

    @Test
    @DisplayName("WL-014 resolveNextWorkout returns first workout without COMPLETED session this cycle")
    fun wl014_resolveNextWorkout() {
        val sessions = listOf(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-a", WorkoutLoggingFixtures.workoutAId),
        )

        val next = CycleProgressResolver.resolveNextWorkout(program, sessions)

        assertEquals(WorkoutLoggingFixtures.workoutBId, next?.id)
    }

    @Test
    @DisplayName("WL-015 isWorkoutCompletedThisCycle is true when COMPLETED session exists")
    fun wl015_isWorkoutCompletedThisCycle() {
        val sessions = listOf(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-a", WorkoutLoggingFixtures.workoutAId),
        )

        val result = CycleProgressResolver.isWorkoutCompletedThisCycle(
            program,
            sessions,
            WorkoutLoggingFixtures.workoutAId,
        )

        assertTrue(result)
    }

    @Test
    @DisplayName("WL-016 isCycleComplete is true when every template workout has COMPLETED session")
    fun wl016_isCycleComplete() {
        val sessions = listOf(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-a", WorkoutLoggingFixtures.workoutAId),
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-b", WorkoutLoggingFixtures.workoutBId),
        )

        val result = CycleProgressResolver.isCycleComplete(program, sessions)

        assertTrue(result)
    }

    @Test
    @DisplayName("WL-017 reconcileCyclePosition counts distinct completed workouts in ledger")
    fun wl017_reconcileCyclePosition() {
        val sessions = listOf(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-a", WorkoutLoggingFixtures.workoutAId),
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-b", WorkoutLoggingFixtures.workoutBId),
        )

        val result = CycleProgressResolver.reconcileCyclePosition(
            WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 0),
            sessions,
        )

        assertEquals(2, result)
    }

    @Test
    @DisplayName("WL-018 Removing completed workout from template does not invalidate other completions")
    fun wl018_midCycleDeleteWorkout() {
        val sessions = listOf(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-a", WorkoutLoggingFixtures.workoutAId),
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-b", WorkoutLoggingFixtures.workoutBId),
        )
        val trimmedProgram = program.copy(workouts = listOf(program.workouts.first()))

        val next = CycleProgressResolver.resolveNextWorkout(trimmedProgram, sessions)
        val stillComplete = CycleProgressResolver.isWorkoutCompletedThisCycle(
            trimmedProgram,
            sessions,
            WorkoutLoggingFixtures.workoutAId,
        )

        assertNull(next)
        assertTrue(stillComplete)
    }

    @Test
    @DisplayName("WL-019 New workout added mid-cycle is scheduled after remaining incomplete trainings")
    fun wl019_midCycleAddWorkout() {
        val sessions = listOf(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-a", WorkoutLoggingFixtures.workoutAId),
        )
        val extended = program.copy(
            cycleLength = 3,
            restSlotIndices = emptySet(),
            workouts = program.workouts + Workout(
                id = WorkoutLoggingFixtures.workoutCId,
                programId = WorkoutLoggingFixtures.programId,
                name = "Workout C",
                weekday = null,
                position = 2,
                workoutLifts = emptyList(),
            ),
        )

        val next = CycleProgressResolver.resolveNextWorkout(extended, sessions)
        // A done → next incomplete training is still B; C is available later in the cycle.
        assertEquals(WorkoutLoggingFixtures.workoutBId, next?.id)

        val afterB =
            sessions +
                WorkoutLoggingFixtures.completedProgramWorkoutSession(
                    "ts-b",
                    WorkoutLoggingFixtures.workoutBId,
                )
        val nextAfterB = CycleProgressResolver.resolveNextWorkout(extended, afterB)
        assertEquals(WorkoutLoggingFixtures.workoutCId, nextAfterB?.id)
    }
}
