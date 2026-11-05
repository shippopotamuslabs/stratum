package com.stratum.application.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.WorkoutLoggingFixtures.workoutAId
import com.stratum.core.testing.training.WorkoutLoggingFixtures.workoutBId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.Program
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus

class UnskipWorkoutUseCaseTest {

    @Test
    @DisplayName("Unskip deletes the completed session for the workout in the current cycle")
    fun unskipDeletesCompletedSession() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 1))
        val session = WorkoutLoggingFixtures.completedProgramWorkoutSession(
            id = "ts-skip-a",
            workoutId = workoutAId,
        )
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        val useCase = UnskipWorkoutUseCase(logRepo, programRepo, FakeLoggingPort())

        val result = useCase(workoutAId)

        assertEquals(UnskipWorkoutResult.Success, result)
        assertTrue(logRepo.getSession(session.id) == null)
    }

    @Test
    @DisplayName("Unskip recomputes cycle position after deletion")
    fun unskipRecomputesCyclePosition() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 1))
        val skipSession = WorkoutLoggingFixtures.completedProgramWorkoutSession(
            id = "ts-skip-a",
            workoutId = workoutAId,
        )
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(skipSession),
            programRepository = programRepo,
        )
        val useCase = UnskipWorkoutUseCase(logRepo, programRepo, FakeLoggingPort())

        useCase(workoutAId)

        assertEquals(0, programRepo.getProgram()?.cyclePosition)
    }

    @Test
    @DisplayName("Unskip is a no-op when the workout has no completed session this cycle")
    fun unskipNoOpWhenNoSession() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val logRepo = FakeTrainingLogRepository(programRepository = programRepo)
        val useCase = UnskipWorkoutUseCase(logRepo, programRepo, FakeLoggingPort())

        val result = useCase(workoutAId)

        assertEquals(UnskipWorkoutResult.Success, result)
        assertEquals(0, programRepo.getProgram()?.cyclePosition)
    }

    @Test
    @DisplayName("Unskip does not delete sessions from a previous cycle")
    fun unskipDoesNotDeletePreviousCycleSessions() = runTest {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram(currentCycleNumber = 2, cyclePosition = 0)
        val programRepo = FakeProgramRepository(program)
        val previousCycleSession = WorkoutLoggingFixtures.completedProgramWorkoutSession(
            id = "ts-old-a",
            workoutId = workoutAId,
            cycleNumber = 1,
        )
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(previousCycleSession),
            programRepository = programRepo,
        )
        val useCase = UnskipWorkoutUseCase(logRepo, programRepo, FakeLoggingPort())

        useCase(workoutAId)

        // The previous-cycle session should still exist — unskip only targets current cycle
        assertTrue(logRepo.getSession(previousCycleSession.id) != null)
    }

    @Test
    @DisplayName("Unskip rest day ignores soft-unlinked sessions from deleted programs")
    fun unskipRestDayIgnoresOrphanedSessions() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val orphanedRest = TrainingSession(
            id = TrainingSessionId("ts-rest-orphan"),
            programId = null,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.REST_DAY,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = Weekday.Tuesday,
            startedAt = Instant.parse("2025-01-01T10:00:00Z"),
            endedAt = Instant.parse("2025-01-01T10:00:00Z"),
            sourceApp = null,
            programNameSnapshot = "Deleted Program",
        )
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(orphanedRest),
            programRepository = programRepo,
        )
        val useCase = UnskipWorkoutUseCase(logRepo, programRepo, FakeLoggingPort())

        useCase(Weekday.Tuesday)

        assertTrue(logRepo.getSession(orphanedRest.id) != null)
    }

    @Test
    @DisplayName("Unskip rest day deletes the matching REST_DAY session")
    fun unskipRestDayDeletesSession() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val restDaySession = TrainingSession(
            id = TrainingSessionId("ts-rest-tue"),
            programId = WorkoutLoggingFixtures.programId,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.REST_DAY,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = Weekday.Tuesday,
            startedAt = Instant.parse("2025-01-01T10:00:00Z"),
            endedAt = Instant.parse("2025-01-01T10:00:00Z"),
            sourceApp = null,
        )
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(restDaySession),
            programRepository = programRepo,
        )
        val useCase = UnskipWorkoutUseCase(logRepo, programRepo, FakeLoggingPort())

        useCase(Weekday.Tuesday)

        assertTrue(logRepo.getSession(restDaySession.id) == null)
    }

    @Test
    @DisplayName("Returns failure when no active program exists")
    fun returnsFailureWhenNoActiveProgram() = runTest {
        val useCase = UnskipWorkoutUseCase(FakeTrainingLogRepository(), FakeProgramRepository(null), FakeLoggingPort())

        val workoutResult = useCase(workoutAId)
        assertEquals(UnskipWorkoutResult.NoActiveProgram, workoutResult)

        val weekdayResult = useCase(Weekday.Tuesday)
        assertEquals(UnskipWorkoutResult.NoActiveProgram, weekdayResult)

        val slotResult = useCase.unskipRestSlot(1)
        assertEquals(UnskipWorkoutResult.NoActiveProgram, slotResult)
    }
}
