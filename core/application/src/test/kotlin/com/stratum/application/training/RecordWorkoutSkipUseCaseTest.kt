package com.stratum.application.training

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionStatus

class RecordWorkoutSkipUseCaseTest {
    @Test
    @DisplayName("WL-027 RecordWorkoutSkipUseCase creates COMPLETED zero-set session")
    fun wl027_workoutSkipZeroSetSession() = runTest {
        val programRepo = FakeProgramRepository(program())
        val repo = FakeTrainingLogRepository(programRepository = programRepo)
        val useCase = RecordWorkoutSkipUseCase(repo, programRepo)

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
        val useCase =
            RecordWorkoutSkipUseCase(
                FakeTrainingLogRepository(programRepository = programRepo),
                programRepo,
            )

        useCase(WorkoutLoggingFixtures.workoutAId).getOrThrow()

        assertEquals(1, programRepo.getProgram()?.cyclePosition)
    }

    @Test
    @DisplayName("WL-030 Completing last workout in cycle wraps cycle number")
    fun wl030_cycleWrapIncrementsCycleNumber() = runTest {
        val programRepo = FakeProgramRepository(
            WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 1, currentCycleNumber = 1),
        )
        val events = FakeProgramChangeEventRepository()
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions =
                    listOf(
                        WorkoutLoggingFixtures.completedProgramWorkoutSession(
                            id = "ts-wkt-a-done",
                            workoutId = WorkoutLoggingFixtures.workoutAId,
                        ),
                    ),
                programRepository = programRepo,
                programChangeEventRepository = events,
            )
        val useCase = RecordWorkoutSkipUseCase(logRepo, programRepo)

        useCase(WorkoutLoggingFixtures.workoutBId).getOrThrow()

        assertEquals(2, programRepo.getProgram()?.currentCycleNumber)
        assertEquals(0, programRepo.getProgram()?.cyclePosition)
    }

    @Test
    @DisplayName("Returns failure when no active program exists")
    fun returnsFailureWhenNoActiveProgram() = runTest {
        val programRepo = FakeProgramRepository(null)
        val useCase = RecordWorkoutSkipUseCase(FakeTrainingLogRepository(), programRepo)

        val result = useCase(WorkoutLoggingFixtures.workoutAId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    @DisplayName("Returns failure when workout does not belong to active program")
    fun returnsFailureWhenWorkoutNotFound() = runTest {
        val programRepo = FakeProgramRepository(program())
        val useCase = RecordWorkoutSkipUseCase(FakeTrainingLogRepository(), programRepo)

        val result = useCase(com.stratum.domain.program.identity.WorkoutId("non-existent"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private fun program() = WorkoutLoggingFixtures.twoWorkoutProgram()
}
