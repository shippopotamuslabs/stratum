package com.stratum.domain.program

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class AddWorkoutLiftUseCaseTest {

    @Test
    @DisplayName("Given active program, when add is called with valid workout ID, then a new lift is appended to that workout")
    fun addSucceedsAndSaves() = runTest {
        val targetWorkoutId = WorkoutId("w-1")
        val newLiftId = LiftId("squat")

        val initialProgram = Program(
            id = ProgramId("p-1"),
            name = "Push Program",
            createdAt = Instant.now(),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = targetWorkoutId,
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = emptyList()
                )
            )
        )

        val repository = FakeProgramRepository(initialProgram)
        val useCase = AddWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository())

        val result = useCase(targetWorkoutId, newLiftId)

        assertTrue(result.isSuccess)
        val updatedProgram = repository.getProgram()
        val workout = updatedProgram?.workouts?.first()
        assertEquals(1, workout?.workoutLifts?.size)
        val addedLift = workout?.workoutLifts?.first()
        assertEquals(newLiftId, addedLift?.liftId)
        assertEquals(0, addedLift?.position)
        assertEquals(1, addedLift?.sets?.size)
        val addedSet = addedLift?.sets?.first()
        assertEquals(8, addedSet?.repMin)
        assertEquals(12, addedSet?.repMax)
        assertEquals(120, addedSet?.restSeconds)
        assertEquals(2, addedSet?.rirGoal)
    }
}
