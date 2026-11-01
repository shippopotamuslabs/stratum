package com.stratum.core.infrastructure.sync.codecs

import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.db.LiftNoteEntity
import com.stratum.core.infrastructure.db.LiftSessionNoteEntity
import com.stratum.core.infrastructure.db.WorkoutLiftEntity
import com.stratum.core.infrastructure.db.WorkoutLiftNoteEntity
import com.stratum.core.infrastructure.sync.FirestoreSyncMetadata
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import com.stratum.domain.training.identity.TrainingSessionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PinnedNoteSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    @Test
    @DisplayName("lift encode/decode round-trips pinned_note")
    fun liftPinnedNote() {
        val entity =
            LiftEntity(
                id = LiftId("lift-1"),
                name = "Squat",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = "",
                isCustom = true,
                enabled = true,
                pinnedNote = "Belt on",
            )
        val encoded = encoders.lift(entity)
        assertTrue("pinned_note" in encoded)
        assertEquals("Belt on", encoded["pinned_note"])
        val decoded =
            requireNotNull(decoders.lift("lift-1", encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L)))
        assertEquals("Belt on", decoded.pinnedNote)
    }

    @Test
    @DisplayName("workout-lift encode/decode round-trips pinned_note")
    fun workoutLiftPinnedNote() {
        val entity =
            WorkoutLiftEntity(
                id = WorkoutLiftId("wl-1"),
                workoutId = WorkoutId("w-1"),
                liftId = LiftId("lift-1"),
                progressionScheme = ProgressionScheme.Double,
                position = 0,
                pinnedNote = "Elbows in",
            )
        val encoded = encoders.workoutLift(entity)
        assertEquals("Elbows in", encoded["pinned_note"])
        val decoded =
            requireNotNull(
                decoders.workoutLift("wl-1", encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L)),
            )
        assertEquals("Elbows in", decoded.pinnedNote)
    }

    @Test
    @DisplayName("session-note encode/decode round-trips body")
    fun sessionNote() {
        val entity =
            LiftSessionNoteEntity(
                id = LiftSessionNoteId("n-1"),
                sessionId = TrainingSessionId("ts-1"),
                liftId = LiftId("lift-1"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                body = "session cue",
            )
        val encoded = encoders.liftSessionNote(entity)
        assertEquals("session cue", encoded["body"])
        val decoded =
            requireNotNull(
                decoders.liftSessionNote("n-1", encoded + (FirestoreSyncMetadata.UPDATED_AT to 1L)),
            )
        assertEquals("session cue", decoded.body)
        assertEquals("ts-1", decoded.sessionId.value)
    }

    @Test
    @DisplayName("cleared pins encode as empty string so merge writes the field")
    fun clearedPinEncodesEmptyString() {
        val lift =
            LiftEntity(
                id = LiftId("lift-1"),
                name = "Squat",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = "",
                isCustom = true,
                enabled = true,
                pinnedNote = null,
            )
        assertEquals("", encoders.lift(lift)["pinned_note"])
        val workoutLift =
            WorkoutLiftEntity(
                id = WorkoutLiftId("wl-1"),
                workoutId = WorkoutId("w-1"),
                liftId = LiftId("lift-1"),
                progressionScheme = ProgressionScheme.Double,
                position = 0,
                pinnedNote = null,
            )
        assertEquals("", encoders.workoutLift(workoutLift)["pinned_note"])
    }

    @Test
    @DisplayName("blank and oversized pinned notes sanitize on decode")
    fun sanitizePinnedNoteOnDecode() {
        val oversized = "x".repeat(2001)
        val liftData =
            encoders.lift(
                LiftEntity(
                    id = LiftId("lift-1"),
                    name = "Squat",
                    movementPatternsBitmask = 0,
                    primaryMusclesBitmask = 0,
                    secondaryMusclesBitmask = 0,
                    presetTags = "",
                    isCustom = true,
                    enabled = true,
                    pinnedNote = oversized,
                ),
            ) + (FirestoreSyncMetadata.UPDATED_AT to 1L)
        assertEquals(2000, decoders.lift("lift-1", liftData)?.pinnedNote?.length)
        val blank =
            requireNotNull(
                decoders.workoutLift(
                    "wl-1",
                    mapOf(
                        "workout_id" to "w-1",
                        "lift_id" to "lift-1",
                        "progression_scheme" to ProgressionScheme.Double.name,
                        "position" to 0,
                        "pinned_note" to "   ",
                        FirestoreSyncMetadata.UPDATED_AT to 1L,
                    ),
                ),
            )
        assertEquals(null, blank.pinnedNote)
    }

    @Test
    @DisplayName("session note order and creation time round-trip for a shared slot")
    fun sessionNoteOrderRoundTrip() {
        fun entity(id: String, body: String, position: Int, createdAt: Long) =
            LiftSessionNoteEntity(
                id = LiftSessionNoteId(id),
                sessionId = TrainingSessionId("ts-1"),
                liftId = LiftId("lift-1"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                body = body,
                position = position,
                createdAtEpochMillis = createdAt,
            )
        val decoded =
            listOf(
                entity("n-1", "first", 0, 10L),
                entity("n-2", "second", 1, 20L),
            ).map { note ->
                requireNotNull(
                    decoders.liftSessionNote(
                        note.id.value,
                        encoders.liftSessionNote(note) + (FirestoreSyncMetadata.UPDATED_AT to 1L),
                    ),
                )
            }.sortedBy { it.position }
        assertEquals(listOf("first", "second"), decoded.map { it.body })
        assertEquals(listOf(10L, 20L), decoded.map { it.createdAtEpochMillis })
    }

    @Test
    @DisplayName("legacy session documents without order fields decode to zero")
    fun sessionNoteLegacyDefaults() {
        val decoded =
            requireNotNull(
                decoders.liftSessionNote(
                    "n-1",
                    mapOf(
                        "session_id" to "ts-1",
                        "lift_id" to "lift-1",
                        "workout_lift_id" to "wl-1",
                        "body" to "legacy",
                        FirestoreSyncMetadata.UPDATED_AT to 1L,
                    ),
                ),
            )
        assertEquals(0, decoded.position)
        assertEquals(0L, decoded.createdAtEpochMillis)
    }

    @Test
    @DisplayName("catalog and slot note rows round-trip parent, order, and creation time")
    fun noteRowRoundTrip() {
        val catalog =
            LiftNoteEntity(
                id = LiftCatalogNoteId("c-1"),
                liftId = LiftId("lift-1"),
                body = "belt on",
                position = 2,
                createdAtEpochMillis = 30L,
            )
        val decodedCatalog =
            requireNotNull(
                decoders.liftCatalogNote(
                    "c-1",
                    encoders.liftCatalogNote(catalog) + (FirestoreSyncMetadata.UPDATED_AT to 1L),
                ),
            )
        assertEquals("lift-1", decodedCatalog.liftId.value)
        assertEquals(2, decodedCatalog.position)
        assertEquals(30L, decodedCatalog.createdAtEpochMillis)

        val slot =
            WorkoutLiftNoteEntity(
                id = WorkoutLiftNoteId("w-1"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                body = "tuck",
                position = 1,
                createdAtEpochMillis = 40L,
            )
        val decodedSlot =
            requireNotNull(
                decoders.workoutLiftNote(
                    "w-1",
                    encoders.workoutLiftNote(slot) + (FirestoreSyncMetadata.UPDATED_AT to 1L),
                ),
            )
        assertEquals("wl-1", decodedSlot.workoutLiftId.value)
        assertEquals(1, decodedSlot.position)
        assertEquals(40L, decodedSlot.createdAtEpochMillis)
    }

    @Test
    @DisplayName("session note body is capped on decode")
    fun sessionNoteBodyCapped() {
        val decoded =
            decoders.liftSessionNote(
                "n-1",
                mapOf(
                    "session_id" to "ts-1",
                    "lift_id" to "lift-1",
                    "workout_lift_id" to "wl-1",
                    "body" to "y".repeat(2001),
                    FirestoreSyncMetadata.UPDATED_AT to 1L,
                ),
            )
        assertEquals(2000, decoded?.body?.length)
    }
}
