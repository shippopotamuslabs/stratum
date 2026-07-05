package com.stratum.domain.training

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RecordWorkoutSkipUseCaseTest {
    @Test
    @DisplayName("WL-027 RecordWorkoutSkipUseCase creates COMPLETED zero-set session")
    fun wl027_workoutSkipZeroSetSession() = runTest {
        val repo = FakeTrainingLogRepository()
        val useCase = RecordWorkoutSkipUseCase(repo, FakeProgramRepository(program()), null)

        val session = useCase(WorkoutLoggingFixtures.workoutAId).getOrThrow()

        assertEquals(TrainingSessionStatus.COMPLETED, session.status)
        assertEquals(WorkoutLoggingFixtures.workoutAId, session.workoutId)
        assertEquals(TrainingSessionKind.PROGRAM_WORKOUT, session.kind)
        assertTrue(repo.getSetsForSession(session.id).isEmpty())
    }

    @Test
    @DisplayName("WL-029 Workout skip increments cycle_position")
    fun wl029_skipAdvancesCyclePosition() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 0))
        val useCase = RecordWorkoutSkipUseCase(FakeTrainingLogRepository(), programRepo, null)

        useCase(WorkoutLoggingFixtures.workoutAId).getOrThrow()

        assertEquals(1, programRepo.getProgram()?.cyclePosition)
    }

    @Test
    @DisplayName("WL-030 Completing last workout in cycle wraps cycle number")
    fun wl030_cycleWrapIncrementsCycleNumber() = runTest {
        val logRepo = FakeTrainingLogRepository()
        logRepo.seedSession(
            WorkoutLoggingFixtures.completedProgramWorkoutSession(
                id = "ts-wkt-a-done",
                workoutId = WorkoutLoggingFixtures.workoutAId,
            ),
        )
        val programRepo = FakeProgramRepository(
            WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 1, currentCycleNumber = 1),
        )
        val useCase = RecordWorkoutSkipUseCase(logRepo, programRepo, FakeProgramChangeEventRepository())

        useCase(WorkoutLoggingFixtures.workoutBId).getOrThrow()

        assertEquals(2, programRepo.getProgram()?.currentCycleNumber)
        assertEquals(0, programRepo.getProgram()?.cyclePosition)
    }

    private fun program() = WorkoutLoggingFixtures.twoWorkoutProgram()
}
