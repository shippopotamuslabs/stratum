package com.stratum.domain.training

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FinalizeTrainingSessionUseCaseTest {
    @Test
    @DisplayName("WL-023 FinalizeTrainingSessionUseCase marks session COMPLETED")
    fun wl023_finalizeMarksCompleted() = runTest {
        val useCase = buildUseCase()

        val result = useCase(TrainingSessionId("ts-live-1"))

        val session = result.getOrThrow()
        assertEquals(TrainingSessionStatus.COMPLETED, session.status)
        assertNotNull(session.endedAt)
    }

    @Test
    @DisplayName("WL-024 Finalize program workout increments cycle_position cache")
    fun wl024_finalizeAdvancesCyclePosition() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 0))
        val useCase = buildUseCase(programRepo)

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        assertEquals(1, programRepo.getProgram()?.cyclePosition)
    }

    @Test
    @DisplayName("WL-025 Finalize AD_HOC session does not change cycle pointer")
    fun wl025_adHocFinalizeNoAdvance() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 0))
        val useCase = buildUseCase(programRepo)

        useCase(TrainingSessionId("ts-adhoc")).getOrThrow()

        assertEquals(0, programRepo.getProgram()?.cyclePosition)
        assertEquals(1, programRepo.getProgram()?.currentCycleNumber)
    }

    @Test
    @DisplayName("WL-039 Finalizing a single workout does not append program_change_events")
    fun wl039_finalizeDoesNotEmitChangeEvent() = runTest {
        val events = FakeProgramChangeEventRepository()
        val useCase = buildUseCase(eventRepository = events)

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        assertEquals(0, events.getEventsForProgram(WorkoutLoggingFixtures.programId).size)
    }

    private suspend fun buildUseCase(
        programRepo: FakeProgramRepository = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram()),
        eventRepository: FakeProgramChangeEventRepository = FakeProgramChangeEventRepository(),
    ): FinalizeTrainingSessionUseCase {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        repo.seedSession(
            WorkoutLoggingFixtures.inProgressSession(
                id = "ts-adhoc",
                workoutId = null,
                kind = TrainingSessionKind.AD_HOC,
            ),
        )
        return FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            programChangeEventRepository = eventRepository,
        )
    }
}
