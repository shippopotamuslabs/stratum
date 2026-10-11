package com.stratum.core.data.sync.codecs

import com.stratum.core.data.db.LoggedSetEntity
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoggedSetEquipmentSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    @Test
    @DisplayName("logged-set sync round-trips equipment_id")
    fun equipmentIdRoundTrip() {
        val entity = loggedSetEntity(equipmentId = EquipmentId("eq-cable"))

        val encoded = encoders.loggedSet(entity)
        assertEquals("eq-cable", encoded["equipment_id"])

        val decoded = requireNotNull(decoders.loggedSet("set-1", encoded))
        assertEquals(EquipmentId("eq-cable"), decoded.equipmentId)
    }

    @Test
    @DisplayName("logged-set sync decodes a missing equipment_id as null (pre-migration cloud docs)")
    fun missingEquipmentIdDecodesNull() {
        val decoded = requireNotNull(decoders.loggedSet("set-1", baseLoggedSetDoc()))

        assertNull(decoded.equipmentId)
    }

    @Test
    @DisplayName("logged-set sync encodes null equipment_id explicitly")
    fun nullEquipmentIdEncodesNull() {
        val encoded = encoders.loggedSet(loggedSetEntity(equipmentId = null))

        assertEquals(null, encoded["equipment_id"])
    }

    @Test
    @DisplayName("logged-set sync decodes a blank equipment_id as null (lenient id contract)")
    fun blankEquipmentIdDecodesNull() {
        val decoded =
            requireNotNull(decoders.loggedSet("set-1", baseLoggedSetDoc() + ("equipment_id" to "  ")))

        assertNull(decoded.equipmentId)
    }

    @Test
    @DisplayName("logged-set sync decodes a blank lift_id as null (unified lenient id contract)")
    fun blankLiftIdDecodesNull() {
        val decoded =
            requireNotNull(decoders.loggedSet("set-1", baseLoggedSetDoc() + ("lift_id" to "")))

        assertNull(decoded.liftId)
    }

    private fun loggedSetEntity(equipmentId: EquipmentId?): LoggedSetEntity =
        LoggedSetEntity(
            id = LoggedSetId("set-1"),
            sessionId = TrainingSessionId("session-1"),
            exerciseNameRaw = "Curl",
            liftId = LiftId("lift-1"),
            equipmentId = equipmentId,
            setIndex = 0,
            weightAmount = 20.0,
            weightUnit = "Lb",
            reps = 10,
            rir = 2,
            completedAtEpochMillis = 0L,
        )

    private fun baseLoggedSetDoc(): Map<String, Any?> =
        mapOf(
            "session_id" to "session-1",
            "exercise_name_raw" to "Curl",
            "lift_id" to "lift-1",
            "set_index" to 0,
            "weight_amount" to 20.0,
            "weight_unit" to "Lb",
            "reps" to 10,
            "completed_at_epoch_millis" to 0L,
            "lift_name" to "Curl",
            "movement_patterns_bitmask" to 0,
            "primary_muscles_bitmask" to 0,
            "secondary_muscles_bitmask" to 0,
            "muscle_length_bias" to "MidRange",
            "updated_at_epoch_millis" to 0L,
        )
}
