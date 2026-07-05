package com.stratum.domain.program

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SaveWorkoutUseCaseTest {
    @Test
    @DisplayName("Given active program when save workout then only target workout is replaced")
    fun saveWorkoutMergesSingleWorkout() = runTest {
        val repository = FakeProgramRepository(TWO_WORKOUT_PROGRAM)
        val useCase = SaveWorkoutUseCase(repository)
        val updatedWorkout =
            TWO_WORKOUT_PROGRAM.workouts.first().copy(
                workoutLifts =
                    listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("bench-press"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets =
                                listOf(
                                    LiftSet(
                                        id = LiftSetId("ls-1"),
                                        workoutLiftId = WorkoutLiftId("wl-1"),
                                        position = 0,
                                        setType = SetType.Standard,
                                        repMin = 6,
                                        repMax = 8,
                                        rirGoal = 1,
                                        isTopSet = true,
                                        restSeconds = 90,
                                    ),
                                ),
                            supersetGroupId = WorkoutSupersetGroupId("sg-1"),
                            useRestTimerBetweenLifts = true,
                        ),
                    ),
            )

        val result = useCase(WorkoutId("w-1"), updatedWorkout)

        assertTrue(result.isSuccess)
        val saved = repository.getProgram()!!
        assertEquals(2, saved.workouts.size)
        assertEquals(1, saved.workouts[0].workoutLifts[0].sets.size)
        assertEquals(90, saved.workouts[0].workoutLifts[0].sets[0].restSeconds)
        assertEquals(WorkoutSupersetGroupId("sg-1"), saved.workouts[0].workoutLifts[0].supersetGroupId)
        assertEquals(2, saved.workouts[1].workoutLifts.size)
    }

    @Test
    @DisplayName("When no active program exists then save returns failure")
    fun saveFailsWhenNoProgram() = runTest {
        val repository = FakeProgramRepository(null)
        val useCase = SaveWorkoutUseCase(repository)

        val result = useCase(WorkoutId("w-1"), sampleWorkout())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    @DisplayName("When workout id is missing then save returns failure")
    fun saveFailsWhenWorkoutNotFound() = runTest {
        val repository = FakeProgramRepository(TWO_WORKOUT_PROGRAM)
        val useCase = SaveWorkoutUseCase(repository)

        val result = useCase(WorkoutId("missing"), sampleWorkout())

        assertTrue(result.isFailure)
        assertEquals(TWO_WORKOUT_PROGRAM, repository.getProgram())
    }

    private fun sampleWorkout(): Workout =
        Workout(
            id = WorkoutId("missing"),
            programId = ProgramId("p-1"),
            name = "Missing",
            weekday = Weekday.Monday,
            position = 0,
            workoutLifts = emptyList(),
        )

    private companion object {
        val TWO_WORKOUT_PROGRAM: Program =
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
                                        sets =
                                            listOf(
                                                LiftSet(
                                                    LiftSetId("ls-1"),
                                                    WorkoutLiftId("wl-1"),
                                                    0,
                                                    SetType.Standard,
                                                    8,
                                                    12,
                                                    2,
                                                    isTopSet = true,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                        Workout(
                            id = WorkoutId("w-2"),
                            programId = ProgramId("p-1"),
                            name = "Pull",
                            weekday = Weekday.Wednesday,
                            position = 1,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-2"),
                                        workoutId = WorkoutId("w-2"),
                                        liftId = LiftId("back-squat"),
                                        progressionScheme = ProgressionScheme.Double,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-3"),
                                        workoutId = WorkoutId("w-2"),
                                        liftId = LiftId("bench-press"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 1,
                                        sets = emptyList(),
                                    ),
                                ),
                        ),
                    ),
            )
    }
}
