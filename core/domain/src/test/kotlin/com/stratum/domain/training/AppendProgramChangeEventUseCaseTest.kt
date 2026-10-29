package com.stratum.domain.training

import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppendProgramChangeEventUseCaseTest {
    @Test
    fun `appended event is stored for its program`() = runTest {
        val repository = FakeProgramChangeEventRepository()
        val useCase = AppendProgramChangeEventUseCase(repository)
        val event =
            ProgramChangeEvent(
                id = ProgramChangeEventId("event-1"),
                programId = ProgramId("program-1"),
                eventType = ProgramChangeEventType.WORKOUT_EDITED,
                occurredAt = Instant.parse("2026-10-29T00:00:00Z"),
                payload = ProgramChangeEventPayload.WorkoutEdited(
                    workoutId = com.stratum.domain.program.identity.WorkoutId("workout-1"),
                ),
            )

        useCase(event)

        assertEquals(listOf(event), repository.getEventsForProgram(ProgramId("program-1")))
    }

    @Test
    fun `events for other programs are not returned`() = runTest {
        val repository = FakeProgramChangeEventRepository()
        val useCase = AppendProgramChangeEventUseCase(repository)
        val event =
            ProgramChangeEvent(
                id = ProgramChangeEventId("event-1"),
                programId = ProgramId("program-1"),
                eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                occurredAt = Instant.parse("2026-10-29T00:00:00Z"),
                payload = ProgramChangeEventPayload.ProgramRegenerated,
            )

        useCase(event)

        assertEquals(emptyList<ProgramChangeEvent>(), repository.getEventsForProgram(ProgramId("program-2")))
    }
}
