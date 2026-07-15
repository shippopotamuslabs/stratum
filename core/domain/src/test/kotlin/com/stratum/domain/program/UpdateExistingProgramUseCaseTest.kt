package com.stratum.domain.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UpdateExistingProgramUseCaseTest {
    @Test
    @DisplayName("Saves program and abandons active session when its workout was removed")
    fun abandonsOrphanedActiveSessionAfterRewrite() = runTest {
        val program = programWithLifts().copy(isActive = true)
        val programRepo = FakeProgramRepository(program)
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(
            WorkoutLoggingFixtures.inProgressSession(
                id = "ts-orphan",
                workoutId = WorkoutLoggingFixtures.workoutAId,
            ),
        )
        val useCase =
            UpdateExistingProgramUseCase(programRepo, trainingLog, FakeLoggingPort())

        val withoutA =
            program.copy(
                workouts = program.workouts.filter { it.id != WorkoutLoggingFixtures.workoutAId },
            )
        val result = useCase(withoutA)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertNull(trainingLog.getSession(TrainingSessionId("ts-orphan")))
        assertTrue(
            programRepo.getProgram()?.workouts?.none {
                it.id == WorkoutLoggingFixtures.workoutAId
            } == true,
        )
    }

    @Test
    @DisplayName("Keeps active session when its workout remains in the program")
    fun keepsActiveSessionWhenWorkoutStillPresent() = runTest {
        val program = programWithLifts().copy(isActive = true)
        val programRepo = FakeProgramRepository(program)
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(
            WorkoutLoggingFixtures.inProgressSession(
                id = "ts-live",
                workoutId = WorkoutLoggingFixtures.workoutAId,
            ),
        )
        val useCase =
            UpdateExistingProgramUseCase(programRepo, trainingLog, FakeLoggingPort())

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
        assertTrue(trainingLog.getSession(TrainingSessionId("ts-live")) != null)
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
