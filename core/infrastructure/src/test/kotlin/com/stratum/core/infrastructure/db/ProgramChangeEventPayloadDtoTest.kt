package com.stratum.core.infrastructure.db

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramChangeEventPayloadDtoTest {

    @Test
    @DisplayName("Given every payload subtype when mapped to DTO then the event type matches")
    fun everySubtypeMapsToItsEventType() {
        val cases =
            listOf(
                ProgramChangeEventPayload.LiftSwapped(
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    previousLiftId = LiftId("old"),
                    newLiftId = LiftId("new"),
                ) to ProgramChangeEventType.LIFT_SWAPPED,
                ProgramChangeEventPayload.WorkoutEdited(
                    workoutId = WorkoutId("w-1"),
                ) to ProgramChangeEventType.WORKOUT_EDITED,
                ProgramChangeEventPayload.ProgramRegenerated to
                    ProgramChangeEventType.PROGRAM_REGENERATED,
                ProgramChangeEventPayload.CycleCompleted(
                    completedCycleNumber = 2,
                ) to ProgramChangeEventType.CYCLE_COMPLETED,
                ProgramChangeEventPayload.SchemeChanged(
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    liftId = LiftId("lift-1"),
                    previous = ProgressionScheme.Linear,
                    current = ProgressionScheme.Double,
                ) to ProgramChangeEventType.SCHEME_CHANGED,
            )

        for ((payload, type) in cases) {
            val dto = payload.toDto()

            assertEquals(type, dto.eventType())
            assertEquals(payload, dto.toDomainPayload())
        }
    }

    @Test
    @DisplayName("Given a DTO with blank ids when mapped to domain then it maps to null")
    fun blankIdsMapToNull() {
        val dto =
            ProgramChangeEventPayloadDto.LiftSwapped(
                workoutLiftId = "   ",
                previousLiftId = "old",
                newLiftId = "new",
            )

        assertNull(dto.toDomainPayload())
    }
}
