package com.stratum.domain.program

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class SwapWorkoutLiftUseCaseTest {

    @Test
    @DisplayName("Given active program, when swap is called with valid workout lift ID, then the database is updated with the new lift ID")
    fun swapSucceedsAndSaves() = runTest {
        val originalLiftId = LiftId("bench-press")
        val newLiftId = LiftId("incline-db-bench")
        val targetWorkoutLiftId = WorkoutLiftId("wl-1")

        val initialProgram = Program(
            id = ProgramId("p-1"),
            name = "Push Program",
            createdAt = Instant.now(),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = targetWorkoutLiftId,
                            workoutId = WorkoutId("w-1"),
                            liftId = originalLiftId,
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = emptyList()
                        )
                    )
                )
            )
        )

        val repository = FakeProgramRepository(initialProgram)
        val useCase = SwapWorkoutLiftUseCase(repository)

        val result = useCase(targetWorkoutLiftId, newLiftId)

        assertTrue(result.isSuccess)
        val updatedProgram = repository.getProgram()
        assertEquals(newLiftId, updatedProgram?.workouts?.first()?.workoutLifts?.first()?.liftId)
    }

    @Test
    @DisplayName("When swap is called and no active program exists, then it returns a failure result")
    fun swapFailsWhenNoProgram() = runTest {
        val repository = FakeProgramRepository(null)
        val useCase = SwapWorkoutLiftUseCase(repository)

        val result = useCase(WorkoutLiftId("wl-1"), LiftId("incline-db-bench"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    @DisplayName("When swap is called with a non-existent workout lift ID, then it returns a failure result and does not modify the program")
    fun swapFailsWhenWorkoutLiftNotFound() = runTest {
        val initialProgram = Program(
            id = ProgramId("p-1"),
            name = "Push Program",
            createdAt = Instant.now(),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("bench-press"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = emptyList()
                        )
                    )
                )
            )
        )

        val repository = FakeProgramRepository(initialProgram)
        val useCase = SwapWorkoutLiftUseCase(repository)

        val result = useCase(WorkoutLiftId("non-existent"), LiftId("incline-db-bench"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        // Verify program was not modified
        val currentProgram = repository.getProgram()
        assertEquals(LiftId("bench-press"), currentProgram?.workouts?.first()?.workoutLifts?.first()?.liftId)
    }
}
