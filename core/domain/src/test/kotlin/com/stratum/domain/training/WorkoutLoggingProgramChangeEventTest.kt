package com.stratum.domain.training

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.identity.ProgramChangeEventId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutLoggingProgramChangeEventTest {
    @Test
    @DisplayName("WL-038 Cycle wrap appends CYCLE_COMPLETED event")
    fun wl038_cycleWrapAppendsCycleCompleted() = runTest {
        val events = FakeProgramChangeEventRepository()
        val programRepo =
            FakeProgramRepository(
                WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 1, currentCycleNumber = 1),
            )
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

        val appended = events.getEventsForProgram(WorkoutLoggingFixtures.programId)
        assertEquals(1, appended.size)
        assertEquals(ProgramChangeEventType.CYCLE_COMPLETED, appended.single().eventType)
        val payload = appended.single().payload as ProgramChangeEventPayload.CycleCompleted
        assertEquals(1, payload.completedCycleNumber)
    }
}
