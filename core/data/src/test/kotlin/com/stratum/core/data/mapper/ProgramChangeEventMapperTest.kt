package com.stratum.core.data.mapper

import com.stratum.core.data.db.ProgramChangeEventEntity
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramChangeEventMapperTest {

    private val eventId = ProgramChangeEventId("pce-1")
    private val programId = ProgramId("program-1")
    private val occurredAt = Instant.parse("2026-09-20T12:00:00Z")

    private fun schemeChangedEvent() =
        ProgramChangeEvent(
            id = eventId,
            programId = programId,
            eventType = ProgramChangeEventType.SCHEME_CHANGED,
            occurredAt = occurredAt,
            payload =
                ProgramChangeEventPayload.SchemeChanged(
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    liftId = LiftId("lift-1"),
                    previous = ProgressionScheme.Linear,
                    current = ProgressionScheme.Double,
                ),
        )

    @Test
    @DisplayName("Given a SchemeChanged event when mapped to entity then payload columns are stored")
    fun schemeChangedToEntityPersistsPayload() {
        val entity = schemeChangedEvent().toEntity(nowMillis = 123L)

        assertEquals(WorkoutLiftId("wl-1"), entity.workoutLiftId)
        assertEquals(LiftId("lift-1"), entity.schemeLiftId)
        assertEquals(ProgressionScheme.Linear, entity.previousScheme)
        assertEquals(ProgressionScheme.Double, entity.currentScheme)
        assertEquals(123L, entity.updatedAtEpochMillis)
    }

    @Test
    @DisplayName("Given a SchemeChanged event when round-tripped then the payload is preserved")
    fun schemeChangedRoundTrips() {
        val domain = schemeChangedEvent().toEntity().toDomain()

        assertEquals(schemeChangedEvent().payload, domain?.payload)
        assertEquals(eventId, domain?.id)
        assertEquals(programId, domain?.programId)
        assertEquals(occurredAt, domain?.occurredAt)
    }

    @Test
    @DisplayName("Given a legacy SchemeChanged row without payload columns when mapped then it is skipped")
    fun legacySchemeChangedRowWithoutPayloadMapsToNull() {
        // Rows written before v6 persisted the scheme payload; the previous/current scheme
        // cannot be reconstructed, so the read path skips them instead of crashing.
        val legacyEntity =
            ProgramChangeEventEntity(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.SCHEME_CHANGED,
                occurredAtEpochMillis = occurredAt.toEpochMilli(),
            )

        assertNull(legacyEntity.toDomain())
    }

    @Test
    @DisplayName("Given a LiftSwapped event when round-tripped then the payload is preserved")
    fun liftSwappedRoundTrips() {
        val event =
            ProgramChangeEvent(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.LIFT_SWAPPED,
                occurredAt = occurredAt,
                payload =
                    ProgramChangeEventPayload.LiftSwapped(
                        workoutLiftId = WorkoutLiftId("wl-1"),
                        previousLiftId = LiftId("lift-old"),
                        newLiftId = LiftId("lift-new"),
                    ),
            )

        assertEquals(event.payload, event.toEntity().toDomain()?.payload)
    }

    @Test
    @DisplayName("Given a WorkoutEdited event when round-tripped then the payload is preserved")
    fun workoutEditedRoundTrips() {
        val event =
            ProgramChangeEvent(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.WORKOUT_EDITED,
                occurredAt = occurredAt,
                payload = ProgramChangeEventPayload.WorkoutEdited(workoutId = WorkoutId("w-1")),
            )

        assertEquals(event.payload, event.toEntity().toDomain()?.payload)
    }

    @Test
    @DisplayName("Given a CycleCompleted event when round-tripped then the payload is preserved")
    fun cycleCompletedRoundTrips() {
        val event =
            ProgramChangeEvent(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.CYCLE_COMPLETED,
                occurredAt = occurredAt,
                payload = ProgramChangeEventPayload.CycleCompleted(completedCycleNumber = 3),
            )

        assertEquals(event.payload, event.toEntity().toDomain()?.payload)
    }

    @Test
    @DisplayName("Given a ProgramRegenerated event when round-tripped then the payload is preserved")
    fun programRegeneratedRoundTrips() {
        val event =
            ProgramChangeEvent(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                occurredAt = occurredAt,
                payload = ProgramChangeEventPayload.ProgramRegenerated,
            )

        assertEquals(event.payload, event.toEntity().toDomain()?.payload)
    }
}
