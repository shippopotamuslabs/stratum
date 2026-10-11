package com.stratum.core.data.sync.codecs

import com.stratum.core.data.db.WorkoutLiftEntity
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutLiftEquipmentSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    @Test
    @DisplayName("workout-lift encode/decode round-trips equipment_id")
    fun equipmentIdRoundTrip() {
        val entity = workoutLiftEntity(equipmentId = EquipmentId("rack"))

        val encoded = encoders.workoutLift(entity)
        assertEquals("rack", encoded["equipment_id"])

        val decoded = requireNotNull(decoders.workoutLift("wl-1", encoded))
        assertEquals(EquipmentId("rack"), decoded.equipmentId)
    }

    @Test
    @DisplayName("workout-lift encode/decode round-trips null equipment_id")
    fun nullEquipmentIdRoundTrip() {
        val encoded = encoders.workoutLift(workoutLiftEntity(equipmentId = null))
        assertNull(encoded["equipment_id"])

        val decoded = requireNotNull(decoders.workoutLift("wl-1", encoded))
        assertNull(decoded.equipmentId)
    }

    @Test
    @DisplayName("workout-lift decodes a missing equipment_id as null (pre-assignment cloud docs)")
    fun missingEquipmentIdDecodesNull() {
        val decoded =
            requireNotNull(
                decoders.workoutLift(
                    "wl-1",
                    mapOf(
                        "workout_id" to "w-1",
                        "lift_id" to "lift-1",
                        "progression_scheme" to "Double",
                        "position" to 0,
                    ),
                ),
            )

        assertNull(decoded.equipmentId)
    }

    private fun workoutLiftEntity(equipmentId: EquipmentId?): WorkoutLiftEntity =
        WorkoutLiftEntity(
            id = WorkoutLiftId("wl-1"),
            workoutId = WorkoutId("w-1"),
            liftId = LiftId("lift-1"),
            equipmentId = equipmentId,
            progressionScheme = ProgressionScheme.Double,
            position = 0,
        )
}
