package com.stratum.application.training

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ObserveCycleProgressUseCaseTest {
    @Test
    @DisplayName("WL-031 ObserveCycleProgressUseCase emits null program when no active program")
    fun wl031_emitsNullProgramWhenMissing() = runTest {
        val useCase =
            ObserveCycleProgressUseCase(
                programRepository = FakeProgramRepository(null),
                trainingLogRepository = FakeTrainingLogRepository(),
            )

        useCase().test {
            val progress = awaitItem()
            assertNull(progress.program)
            assertNull(progress.nextWorkout)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("WL-032 ObserveCycleProgressUseCase resolves next workout from ledger")
    fun wl032_resolvesNextWorkout() = runTest {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram()
        val useCase =
            ObserveCycleProgressUseCase(
                programRepository = FakeProgramRepository(program),
                trainingLogRepository =
                    FakeTrainingLogRepository(
                        initialSessions =
                            listOf(
                                WorkoutLoggingFixtures.completedProgramWorkoutSession(
                                    id = "done-a",
                                    workoutId = WorkoutLoggingFixtures.workoutAId,
                                ),
                            ),
                    ),
            )

        useCase().test {
            val progress = awaitItem()
            assertEquals(WorkoutLoggingFixtures.workoutBId, progress.nextWorkout?.id)
            assertEquals(setOf(WorkoutLoggingFixtures.workoutAId), progress.completedWorkoutIdsThisCycle)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
