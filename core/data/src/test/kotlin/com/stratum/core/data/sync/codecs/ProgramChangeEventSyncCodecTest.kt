package com.stratum.core.data.sync.codecs

import com.stratum.core.infrastructure.mapper.toEntity
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.ProgramId
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

class ProgramChangeEventSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    private fun schemeChangedEntity() =
        ProgramChangeEvent(
            id = ProgramChangeEventId("evt-1"),
            programId = ProgramId("prog-1"),
            eventType = ProgramChangeEventType.SCHEME_CHANGED,
            occurredAt = Instant.ofEpochMilli(1234567890L),
            payload =
                ProgramChangeEventPayload.SchemeChanged(
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    liftId = LiftId("lift-1"),
                    previous = ProgressionScheme.Linear,
                    current = ProgressionScheme.Double,
                ),
        ).toEntity(nowMillis = 1234567890L)

    @Test
    @DisplayName("scheme_changed encode/decode round-trips the JSON payload")
    fun schemeChangedRoundTrip() {
        val entity = schemeChangedEntity()
        val encoded = encoders.programChangeEvent(entity)

        val decoded =
            requireNotNull(
                decoders.programChangeEvent(
                    "evt-1",
                    encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L),
                ),
            )
        assertEquals(entity.payloadJson, decoded.payloadJson)
    }

    @Test
    @DisplayName("encode derives legacy flat fields for pre-v6 readers")
    fun encodeDerivesLegacyFlats() {
        val encoded = encoders.programChangeEvent(schemeChangedEntity())

        // SCHEME_CHANGED has no pre-v6 flat representation beyond the slot id; the JSON
        // blob stays the source of truth for new readers.
        assertEquals("wl-1", encoded["workout_lift_id"])
        assertEquals(schemeChangedEntity().payloadJson, encoded["payload_json"])
    }

    @Test
    @DisplayName("lift_swapped encode derives legacy flat fields for pre-v6 readers")
    fun liftSwappedEncodeDerivesLegacyFlats() {
        val entity =
            ProgramChangeEvent(
                id = ProgramChangeEventId("evt-2"),
                programId = ProgramId("prog-1"),
                eventType = ProgramChangeEventType.LIFT_SWAPPED,
                occurredAt = Instant.ofEpochMilli(1234567890L),
                payload =
                    ProgramChangeEventPayload.LiftSwapped(
                        workoutLiftId = WorkoutLiftId("wl-1"),
                        previousLiftId = LiftId("lift-old"),
                        newLiftId = LiftId("lift-new"),
                    ),
            ).toEntity()
        val encoded = encoders.programChangeEvent(entity)

        assertEquals("wl-1", encoded["workout_lift_id"])
        assertEquals("lift-old", encoded["previous_lift_id"])
        assertEquals("lift-new", encoded["new_lift_id"])

        val decoded =
            requireNotNull(
                decoders.programChangeEvent(
                    "evt-2",
                    encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L),
                ),
            )
        assertEquals(entity.payloadJson, decoded.payloadJson)
    }

    @Test
    @DisplayName("legacy document without payload_json decodes from flat fields")
    fun legacyDocumentDecodesFromFlats() {
        val encoded =
            mapOf(
                "program_id" to "prog-1",
                "event_type" to "LIFT_SWAPPED",
                "occurred_at_epoch_millis" to 1234567890L,
                "workout_lift_id" to "wl-1",
                "previous_lift_id" to "lift-old",
                "new_lift_id" to "lift-new",
            )
        val decoded =
            requireNotNull(
                decoders.programChangeEvent(
                    "evt-legacy",
                    encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L),
                ),
            )
        assertNull(decoded.payloadJson)
        assertEquals(WorkoutLiftId("wl-1"), decoded.workoutLiftId)
    }

    @Test
    @DisplayName("document with corrupt payload_json fails decode")
    fun corruptPayloadJsonFailsDecode() {
        val encoded =
            mapOf(
                "program_id" to "prog-1",
                "event_type" to "SCHEME_CHANGED",
                "occurred_at_epoch_millis" to 1234567890L,
                "payload_json" to "{not valid json",
            )

        assertNull(
            decoders.programChangeEvent(
                "evt-corrupt",
                encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L),
            ),
        )
    }

    @Test
    @DisplayName("document with mismatched payload_json event type fails decode")
    fun mismatchedPayloadJsonFailsDecode() {
        val encoded =
            encoders.programChangeEvent(schemeChangedEntity()).toMutableMap().apply {
                put("event_type", "WORKOUT_EDITED")
            }

        assertNull(
            decoders.programChangeEvent(
                "evt-mismatch",
                encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L),
            ),
        )
    }
}
