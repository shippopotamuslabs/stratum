package com.stratum.application.program

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.domain.lift.DEFAULT_HIGH_FATIGUE_REST_SECONDS
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout

class AddWorkoutLiftUseCaseTest {

    @Test
    @DisplayName("Given active program, when add is called with valid workout ID, then a new lift is appended to that workout")
    fun addSucceedsAndSaves() = runTest {
        val targetWorkoutId = WorkoutId("w-1")
        val newLiftId = LiftId("squat")
        val compoundSquat =
            Lift(
                id = newLiftId,
                name = "Squat",
                movementPatterns =
                    setOf(MovementPattern.KneeExtension, MovementPattern.HipExtension),
                primaryMuscles = setOf(MuscleGroup.Quad),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                // High fatigue → 180s rest (rest is no longer driven by multi-pattern isCompound)
                fatigueProfile = FatigueProfile.High,
            )

        val initialProgram =
            Program(
                id = ProgramId("p-1"),
                name = "Push Program",
                createdAt = Instant.now(),
                cycleLength = 1,
                trainingWeekdays = setOf(Weekday.Monday),
                workouts =
                    listOf(
                        Workout(
                            id = targetWorkoutId,
                            programId = ProgramId("p-1"),
                            name = "Push Day",
                            weekday = Weekday.Monday,
                            position = 0,
                            workoutLifts = emptyList(),
                        ),
                    ),
            )

        val repository = FakeProgramRepository(initialProgram)
        val useCase =
            AddWorkoutLiftUseCase(
                repository,
                NoOpProgramChangeEventRepository(),
                FakeLiftRepository(listOf(compoundSquat)),
            )

        val result = useCase(targetWorkoutId, newLiftId)

        assertTrue(result.isSuccess)
        val workout = repository.getProgram()?.workouts?.first()
        assertEquals(1, workout?.workoutLifts?.size)
        val addedLift = workout?.workoutLifts?.first()
        assertEquals(newLiftId, addedLift?.liftId)
        assertEquals(0, addedLift?.position)
        assertEquals(1, addedLift?.sets?.size)
        val addedSet = addedLift?.sets?.first()
        assertEquals(8, addedSet?.repMin)
        assertEquals(12, addedSet?.repMax)
        assertEquals(DEFAULT_HIGH_FATIGUE_REST_SECONDS, addedSet?.restSeconds)
        assertEquals(2, addedSet?.rirGoal)
    }

    @Test
    @DisplayName("Given lift with rest override when added then sets use override seconds")
    fun usesLiftRestOverride() = runTest {
        val targetWorkoutId = WorkoutId("w-1")
        val newLiftId = LiftId("curl")
        val isolationWithOverride =
            Lift(
                id = newLiftId,
                name = "Curl",
                movementPatterns = setOf(MovementPattern.ElbowFlexion),
                primaryMuscles = setOf(MuscleGroup.Bicep),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                defaultRestSeconds = 90,
            )
        val repository =
            FakeProgramRepository(
                Program(
                    id = ProgramId("p-1"),
                    name = "Arm Program",
                    createdAt = Instant.now(),
                    cycleLength = 1,
                    trainingWeekdays = setOf(Weekday.Monday),
                    workouts =
                        listOf(
                            Workout(
                                id = targetWorkoutId,
                                programId = ProgramId("p-1"),
                                name = "Arms",
                                weekday = Weekday.Monday,
                                position = 0,
                                workoutLifts = emptyList(),
                            ),
                        ),
                ),
            )
        val useCase =
            AddWorkoutLiftUseCase(
                repository,
                NoOpProgramChangeEventRepository(),
                FakeLiftRepository(listOf(isolationWithOverride)),
            )

        val result = useCase(targetWorkoutId, newLiftId)

        assertTrue(result.isSuccess)
        assertEquals(
            90,
            repository.getProgram()?.workouts?.first()?.workoutLifts?.first()?.sets?.first()?.restSeconds,
        )
    }
}
