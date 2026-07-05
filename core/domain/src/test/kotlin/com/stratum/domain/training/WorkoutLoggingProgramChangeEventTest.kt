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
        val logRepo = FakeTrainingLogRepository()
        logRepo.seedSession(
            WorkoutLoggingFixtures.completedProgramWorkoutSession(
                id = "ts-wkt-a-done",
                workoutId = WorkoutLoggingFixtures.workoutAId,
            ),
        )
        val events = FakeProgramChangeEventRepository()
        val useCase = RecordWorkoutSkipUseCase(
            logRepo,
            FakeProgramRepository(
                WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 1, currentCycleNumber = 1),
            ),
            events,
        )

        useCase(WorkoutLoggingFixtures.workoutBId).getOrThrow()

        val appended = events.getEventsForProgram(WorkoutLoggingFixtures.programId)
        assertEquals(1, appended.size)
        assertEquals(ProgramChangeEventType.CYCLE_COMPLETED, appended.single().eventType)
        val payload = appended.single().payload as ProgramChangeEventPayload.CycleCompleted
        assertEquals(1, payload.completedCycleNumber)
    }
}
