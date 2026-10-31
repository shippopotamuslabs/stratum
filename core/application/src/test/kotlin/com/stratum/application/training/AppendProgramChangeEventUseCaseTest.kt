package com.stratum.application.training

import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutLiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.workout.LiftSwapped
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType

class AppendProgramChangeEventUseCaseTest {
    @Test
    fun `appendSchemeChanged stores a SCHEME_CHANGED event with the scheme payload`() = runTest {
        val repository = FakeProgramChangeEventRepository()
        val useCase = AppendProgramChangeEventUseCase(repository)

        useCase.appendSchemeChanged(
            programId = ProgramId("program-1"),
            workoutLiftId = WorkoutLiftId("wl-1"),
            liftId = LiftId("bench-press"),
            previous = ProgressionScheme.Linear,
            current = ProgressionScheme.Double,
        )

        val events = repository.getEventsForProgram(ProgramId("program-1")).events
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(ProgramChangeEventType.SCHEME_CHANGED, event.eventType)
        assertEquals(
            ProgramChangeEventPayload.SchemeChanged(
                workoutLiftId = WorkoutLiftId("wl-1"),
                liftId = LiftId("bench-press"),
                previous = ProgressionScheme.Linear,
                current = ProgressionScheme.Double,
            ),
            event.payload,
        )
        assertTrue(event.id.value.isNotBlank())
    }

    @Test
    fun `appendLiftSwapped stores a LIFT_SWAPPED event with the lift payload`() = runTest {
        val repository = FakeProgramChangeEventRepository()
        val useCase = AppendProgramChangeEventUseCase(repository)

        useCase.appendLiftSwapped(
            programId = ProgramId("program-1"),
            workoutLiftId = WorkoutLiftId("wl-1"),
            previousLiftId = LiftId("bench-press"),
            newLiftId = LiftId("incline-press"),
        )

        val events = repository.getEventsForProgram(ProgramId("program-1")).events
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(ProgramChangeEventType.LIFT_SWAPPED, event.eventType)
        assertEquals(
            ProgramChangeEventPayload.LiftSwapped(
                workoutLiftId = WorkoutLiftId("wl-1"),
                previousLiftId = LiftId("bench-press"),
                newLiftId = LiftId("incline-press"),
            ),
            event.payload,
        )
        assertEquals(emptyList<ProgramChangeEvent>(), repository.getEventsForProgram(ProgramId("program-2")).events)
    }
}
