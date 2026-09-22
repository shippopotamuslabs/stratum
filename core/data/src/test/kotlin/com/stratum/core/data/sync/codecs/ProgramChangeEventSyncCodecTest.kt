package com.stratum.core.data.sync.codecs

import com.stratum.core.data.db.ProgramChangeEventEntity
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramChangeEventSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    @Test
    @DisplayName("scheme_changed encode/decode round-trips scheme payload")
    fun schemeChangedRoundTrip() {
        val entity =
            ProgramChangeEventEntity(
                id = ProgramChangeEventId("evt-1"),
                programId = ProgramId("prog-1"),
                eventType = ProgramChangeEventType.SCHEME_CHANGED,
                occurredAtEpochMillis = 1234567890L,
                workoutLiftId = null,
                previousLiftId = null,
                newLiftId = null,
                workoutId = null,
                completedCycleNumber = null,
                schemeLiftId = LiftId("lift-1"),
                previousScheme = ProgressionScheme.Linear,
                currentScheme = ProgressionScheme.Double,
                updatedAtEpochMillis = 1234567890L,
                pendingSync = 0,
            )
        val encoded = encoders.programChangeEvent(entity)
        assertEquals("lift-1", encoded["scheme_lift_id"])
        assertEquals("Linear", encoded["previous_scheme"])
        assertEquals("Double", encoded["current_scheme"])

        val decoded =
            requireNotNull(
                decoders.programChangeEvent(
                    "evt-1",
                    encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L),
                ),
            )
        assertEquals(LiftId("lift-1"), decoded.schemeLiftId)
        assertEquals(ProgressionScheme.Linear, decoded.previousScheme)
        assertEquals(ProgressionScheme.Double, decoded.currentScheme)
    }

    @Test
    @DisplayName("legacy scheme_changed document without payload decodes with null scheme fields")
    fun legacySchemeChangedDecodesWithNulls() {
        val encoded =
            mapOf(
                "program_id" to "prog-1",
                "event_type" to "SCHEME_CHANGED",
                "occurred_at_epoch_millis" to 1234567890L,
            )
        val decoded =
            requireNotNull(
                decoders.programChangeEvent(
                    "evt-legacy",
                    encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L),
                ),
            )
        assertEquals(null, decoded.schemeLiftId)
        assertEquals(null, decoded.previousScheme)
        assertEquals(null, decoded.currentScheme)
    }
}
