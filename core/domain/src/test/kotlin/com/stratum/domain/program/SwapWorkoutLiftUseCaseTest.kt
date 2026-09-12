package com.stratum.domain.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
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
        val useCase = SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), FakeLoggingPort())

        val result = useCase(targetWorkoutLiftId, newLiftId)

        assertTrue(result.isSuccess)
        val updatedProgram = repository.getProgram()
        assertEquals(newLiftId, updatedProgram?.workouts?.first()?.workoutLifts?.first()?.liftId)
    }

    @Test
    @DisplayName("When swap is called and no active program exists, then it returns a failure result")
    fun swapFailsWhenNoProgram() = runTest {
        val logging = FakeLoggingPort()
        val repository = FakeProgramRepository(null)
        val useCase = SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), logging)

        val result = useCase(WorkoutLiftId("wl-1"), LiftId("incline-db-bench"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(1, logging.errors.size)
        assertTrue(logging.errors.first().message.contains("No active program found"))
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
        val logging = FakeLoggingPort()
        val useCase = SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), logging)

        val result = useCase(WorkoutLiftId("non-existent"), LiftId("incline-db-bench"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        // Verify program was not modified
        val currentProgram = repository.getProgram()
        assertEquals(LiftId("bench-press"), currentProgram?.workouts?.first()?.workoutLifts?.first()?.liftId)
        assertEquals(1, logging.errors.size)
        assertTrue(logging.errors.first().message.contains("not found in the program"))
    }

    @Test
    @DisplayName("WL-035 SwapWorkoutLiftUseCase appends LIFT_SWAPPED event")
    fun wl035_swapAppendsLiftSwappedEvent() = runTest {
        val events = com.stratum.core.testing.training.FakeProgramChangeEventRepository()
        val program = Program(
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
                            id = WorkoutLiftId("wl-bench"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("lift-bench"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = emptyList(),
                        ),
                    ),
                ),
            ),
        )
        val useCase = SwapWorkoutLiftUseCase(FakeProgramRepository(program), events, FakeLoggingPort())

        useCase(WorkoutLiftId("wl-bench"), LiftId("lift-squat")).getOrThrow()

        val appended = events.getEventsForProgram(ProgramId("p-1"))
        assertEquals(1, appended.size)
        assertEquals(com.stratum.domain.training.ProgramChangeEventType.LIFT_SWAPPED, appended.single().eventType)
        val payload = appended.single().payload as com.stratum.domain.training.ProgramChangeEventPayload.LiftSwapped
        assertEquals(LiftId("lift-bench"), payload.previousLiftId)
        assertEquals(LiftId("lift-squat"), payload.newLiftId)
    }
}
