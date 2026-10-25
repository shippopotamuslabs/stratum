package com.stratum.domain.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UpdateExistingProgramUseCaseTest {
    @Test
    @DisplayName("Persists program template without auto-abandoning sessions")
    fun savesProgramTemplate() = runTest {
        val program = programWithLifts().activate()
        val programRepo = FakeProgramRepository(program)
        val useCase = UpdateExistingProgramUseCase(programRepo, FakeLoggingPort())

        val renamed =
            program.copy(
                workouts =
                    program.workouts.map { workout ->
                        if (workout.id == WorkoutLoggingFixtures.workoutAId) {
                            workout.copy(name = "Renamed A")
                        } else {
                            workout
                        }
                    },
            )
        val result = useCase(renamed)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertEquals(
            "Renamed A",
            programRepo.getProgram()?.workouts
                ?.first { it.id == WorkoutLoggingFixtures.workoutAId }
                ?.name,
        )
    }

    @Test
    @DisplayName("Given program without workouts when invoked then failure without non-fatal")
    fun emptyProgramFailsWithoutNonFatal() = runTest {
        val program = programWithLifts().copy(workouts = emptyList())
        val logging = FakeLoggingPort()
        val useCase = UpdateExistingProgramUseCase(FakeProgramRepository(program), logging)

        val result = useCase(program)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, logging.errors.size)
        assertEquals(1, logging.warnings.size)
    }

    @Test
    @DisplayName("Given program without exercises when invoked then failure without non-fatal")
    fun exerciselessProgramFailsWithoutNonFatal() = runTest {
        val base = programWithLifts()
        val program = base.copy(workouts = base.workouts.map { it.copy(workoutLifts = emptyList()) })
        val logging = FakeLoggingPort()
        val useCase = UpdateExistingProgramUseCase(FakeProgramRepository(program), logging)

        val result = useCase(program)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, logging.errors.size)
        assertEquals(1, logging.warnings.size)
    }

    private fun programWithLifts(): Program {
        val base = WorkoutLoggingFixtures.twoWorkoutProgram()
        return base.copy(
            workouts =
                base.workouts.mapIndexed { index, workout ->
                    workout.copy(
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = WorkoutLiftId("wl-$index"),
                                    workoutId = workout.id,
                                    liftId = WorkoutLoggingFixtures.liftBenchId,
                                    progressionScheme = ProgressionScheme.Linear,
                                    position = 0,
                                    sets =
                                        listOf(
                                            LiftSet(
                                                id = LiftSetId("ls-$index"),
                                                workoutLiftId = WorkoutLiftId("wl-$index"),
                                                position = 0,
                                                setType = SetType.Standard,
                                                repMin = 8,
                                                repMax = 12,
                                                rirGoal = 2,
                                                isTopSet = true,
                                            ),
                                        ),
                                ),
                            ),
                    )
                },
        )
    }
}
