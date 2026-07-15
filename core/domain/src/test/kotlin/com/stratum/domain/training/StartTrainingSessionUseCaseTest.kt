package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StartTrainingSessionUseCaseTest {
    @Test
    @DisplayName("WL-020 StartTrainingSessionUseCase ProgramWorkout creates IN_PROGRESS session")
    fun wl020_startProgramWorkout() = runTest {
        val useCase = StartTrainingSessionUseCase(
            FakeTrainingLogRepository(),
            FakeProgramRepository(program()),
            FakeLoggingPort(),
        )

        val result = useCase(StartTrainingSessionRequest.ProgramWorkout(WorkoutLoggingFixtures.workoutAId))

        val session = result.getOrThrow()
        assertEquals(TrainingSessionStatus.IN_PROGRESS, session.status)
        assertEquals(WorkoutLoggingFixtures.workoutAId, session.workoutId)
        assertEquals(TrainingSessionKind.PROGRAM_WORKOUT, session.kind)
        assertEquals(1, session.cycleNumberSnapshot)
    }

    @Test
    @DisplayName("WL-021 StartTrainingSessionUseCase AdHocEmpty creates AD_HOC session")
    fun wl021_startAdHocEmpty() = runTest {
        val useCase = StartTrainingSessionUseCase(
            FakeTrainingLogRepository(),
            FakeProgramRepository(program()),
            FakeLoggingPort(),
        )

        val result = useCase(StartTrainingSessionRequest.AdHocEmpty)

        val session = result.getOrThrow()
        assertEquals(TrainingSessionKind.AD_HOC, session.kind)
        assertNull(session.workoutId)
        assertEquals(TrainingSessionStatus.IN_PROGRESS, session.status)
    }

    @Test
    @DisplayName("WL-022 Second start throws ActiveTrainingSessionExistsException")
    fun wl022_secondStartThrowsConflict() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-active"))
        val useCase = StartTrainingSessionUseCase(
            repo,
            FakeProgramRepository(program()),
            FakeLoggingPort(),
        )

        val firstStart = useCase(StartTrainingSessionRequest.ProgramWorkout(WorkoutLoggingFixtures.workoutBId))
        assertTrue(firstStart.isFailure)
        val error = firstStart.exceptionOrNull()
        assertTrue(error is ActiveTrainingSessionExistsException)
        assertEquals(
            TrainingSessionId("ts-active"),
            (error as ActiveTrainingSessionExistsException).activeSessionId,
        )
    }

    @Test
    @DisplayName("Orphaned active session (deleted workout) is abandoned so a new start can succeed")
    fun orphanedActiveSessionIsAbandonedOnStart() = runTest {
        val programRepo = FakeProgramRepository(program())
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(
            WorkoutLoggingFixtures.inProgressSession(
                id = "ts-orphan",
                workoutId = WorkoutId("deleted-mid-session"),
            ),
        )
        val useCase = StartTrainingSessionUseCase(
            trainingLog,
            programRepo,
            FakeLoggingPort(),
        )

        val result =
            useCase(StartTrainingSessionRequest.ProgramWorkout(WorkoutLoggingFixtures.workoutBId))

        val session = result.getOrThrow()
        assertEquals(WorkoutLoggingFixtures.workoutBId, session.workoutId)
        assertNull(trainingLog.getSession(TrainingSessionId("ts-orphan")))
    }

    private fun program() = WorkoutLoggingFixtures.twoWorkoutProgram()
}
