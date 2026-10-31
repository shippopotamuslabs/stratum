package com.stratum.core.infrastructure.mapper

import com.stratum.core.infrastructure.db.ProgramChangeEventEntity
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramChangeEventMapperTest {

    private val eventId = ProgramChangeEventId("pce-1")
    private val programId = ProgramId("program-1")
    private val occurredAt = Instant.parse("2026-09-20T12:00:00Z")

    private fun event(
        type: ProgramChangeEventType,
        payload: ProgramChangeEventPayload,
    ) = ProgramChangeEvent(
        id = eventId,
        programId = programId,
        eventType = type,
        occurredAt = occurredAt,
        payload = payload,
    )

    private fun schemeChangedEvent() =
        event(
            type = ProgramChangeEventType.SCHEME_CHANGED,
            payload =
                ProgramChangeEventPayload.SchemeChanged(
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    liftId = LiftId("lift-1"),
                    previous = ProgressionScheme.Linear,
                    current = ProgressionScheme.Double,
                ),
        )

    @Test
    @DisplayName("Given a SchemeChanged event when mapped to entity then the JSON payload is stored")
    fun schemeChangedToEntityPersistsJsonPayload() {
        val entity = schemeChangedEvent().toEntity(nowMillis = 123L)

        assertTrue(entity.payloadJson?.contains("SCHEME_CHANGED") == true)
        assertTrue(entity.payloadJson?.contains("wl-1") == true)
        assertEquals(123L, entity.updatedAtEpochMillis)
    }

    @Test
    @DisplayName("Given a new event when mapped to entity then legacy flat columns stay null")
    fun newWritesLeaveLegacyColumnsNull() {
        val entity = schemeChangedEvent().toEntity()

        assertNull(entity.workoutLiftId)
        assertNull(entity.previousLiftId)
        assertNull(entity.newLiftId)
        assertNull(entity.workoutId)
        assertNull(entity.completedCycleNumber)
    }

    @Test
    @DisplayName("Given an eventType/payload mismatch when mapped to entity then it fails fast")
    fun mismatchedEventTypeFailsFast() {
        val mismatched =
            event(
                type = ProgramChangeEventType.LIFT_SWAPPED,
                payload = ProgramChangeEventPayload.WorkoutEdited(workoutId = WorkoutId("w-1")),
            )

        assertThrows(IllegalArgumentException::class.java) {
            mismatched.toEntity()
        }
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
    @DisplayName("Given a corrupt JSON payload when mapped then it maps to null")
    fun corruptJsonMapsToNull() {
        val entity =
            ProgramChangeEventEntity(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.SCHEME_CHANGED,
                occurredAtEpochMillis = occurredAt.toEpochMilli(),
                payloadJson = "{not valid json",
            )

        assertNull(entity.toDomain())
    }

    @Test
    @DisplayName("Given a JSON payload of another event type when mapped then it maps to null")
    fun mismatchedJsonPayloadMapsToNull() {
        val swappedJson =
            event(
                type = ProgramChangeEventType.LIFT_SWAPPED,
                payload =
                    ProgramChangeEventPayload.LiftSwapped(
                        workoutLiftId = WorkoutLiftId("wl-1"),
                        previousLiftId = LiftId("lift-old"),
                        newLiftId = LiftId("lift-new"),
                    ),
            ).toEntity().payloadJson
        val entity =
            ProgramChangeEventEntity(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.WORKOUT_EDITED,
                occurredAtEpochMillis = occurredAt.toEpochMilli(),
                payloadJson = swappedJson,
            )

        assertNull(entity.toDomain())
    }

    @Test
    @DisplayName("Given a legacy SchemeChanged row without JSON when mapped then it is skipped")
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
    @DisplayName("Given a legacy LiftSwapped row when mapped then the payload is reconstructed")
    fun legacyLiftSwappedRowReconstructs() {
        val legacyEntity =
            ProgramChangeEventEntity(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.LIFT_SWAPPED,
                occurredAtEpochMillis = occurredAt.toEpochMilli(),
                workoutLiftId = WorkoutLiftId("wl-1"),
                previousLiftId = LiftId("lift-old"),
                newLiftId = LiftId("lift-new"),
            )

        assertEquals(
            ProgramChangeEventPayload.LiftSwapped(
                workoutLiftId = WorkoutLiftId("wl-1"),
                previousLiftId = LiftId("lift-old"),
                newLiftId = LiftId("lift-new"),
            ),
            legacyEntity.toDomain()?.payload,
        )
    }

    @Test
    @DisplayName("Given a legacy LiftSwapped row with missing columns when mapped then it is skipped")
    fun legacyLiftSwappedRowWithMissingColumnsMapsToNull() {
        val legacyEntity =
            ProgramChangeEventEntity(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.LIFT_SWAPPED,
                occurredAtEpochMillis = occurredAt.toEpochMilli(),
                workoutLiftId = WorkoutLiftId("wl-1"),
                previousLiftId = null,
                newLiftId = LiftId("lift-new"),
            )

        assertNull(legacyEntity.toDomain())
    }

    @Test
    @DisplayName("Given a legacy WorkoutEdited row with missing workout id when mapped then it is skipped")
    fun legacyWorkoutEditedRowWithMissingColumnsMapsToNull() {
        val legacyEntity =
            ProgramChangeEventEntity(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.WORKOUT_EDITED,
                occurredAtEpochMillis = occurredAt.toEpochMilli(),
                workoutId = null,
            )

        assertNull(legacyEntity.toDomain())
    }

    @Test
    @DisplayName("Given a legacy CycleCompleted row with missing count when mapped then it is skipped")
    fun legacyCycleCompletedRowWithMissingColumnsMapsToNull() {
        val legacyEntity =
            ProgramChangeEventEntity(
                id = eventId,
                programId = programId,
                eventType = ProgramChangeEventType.CYCLE_COMPLETED,
                occurredAtEpochMillis = occurredAt.toEpochMilli(),
                completedCycleNumber = null,
            )

        assertNull(legacyEntity.toDomain())
    }

    @Test
    @DisplayName("Given a LiftSwapped event when round-tripped then the payload is preserved")
    fun liftSwappedRoundTrips() {
        val event =
            event(
                type = ProgramChangeEventType.LIFT_SWAPPED,
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
            event(
                type = ProgramChangeEventType.WORKOUT_EDITED,
                payload = ProgramChangeEventPayload.WorkoutEdited(workoutId = WorkoutId("w-1")),
            )

        assertEquals(event.payload, event.toEntity().toDomain()?.payload)
    }

    @Test
    @DisplayName("Given a CycleCompleted event when round-tripped then the payload is preserved")
    fun cycleCompletedRoundTrips() {
        val event =
            event(
                type = ProgramChangeEventType.CYCLE_COMPLETED,
                payload = ProgramChangeEventPayload.CycleCompleted(completedCycleNumber = 3),
            )

        assertEquals(event.payload, event.toEntity().toDomain()?.payload)
    }

    @Test
    @DisplayName("Given a ProgramRegenerated event when round-tripped then the payload is preserved")
    fun programRegeneratedRoundTrips() {
        val event =
            event(
                type = ProgramChangeEventType.PROGRAM_REGENERATED,
                payload = ProgramChangeEventPayload.ProgramRegenerated,
            )

        assertEquals(event.payload, event.toEntity().toDomain()?.payload)
    }
}
