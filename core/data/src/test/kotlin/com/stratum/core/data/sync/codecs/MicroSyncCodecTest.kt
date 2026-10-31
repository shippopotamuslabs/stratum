package com.stratum.core.data.sync.codecs

import com.stratum.core.infrastructure.db.EquipmentEntity
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MicroSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    @Test
    @DisplayName("equipment encode/decode round-trips stops")
    fun equipmentRoundTrips() {
        val entity =
            EquipmentEntity(
                id = EquipmentId("pin"),
                name = "Pin",
                isCustom = true,
                microOptionsJson = "[5.0,10.0]",
            )
        val encoded = encoders.equipment(entity)
        assertEquals(listOf(5.0, 10.0), encoded["micro_options"])
        val decoded = requireNotNull(decoders.equipment("pin", encoded + updatedAt()))
        assertEquals("[5.0,10.0]", decoded.microOptionsJson)
    }

    @Test
    @DisplayName("equipment null micro encodes null and decodes null")
    fun equipmentNullRoundTrips() {
        val entity =
            EquipmentEntity(
                id = EquipmentId("cable"),
                name = "Cables",
                isCustom = false,
            )
        val encoded = encoders.equipment(entity)
        assertNull(encoded["micro_options"])
        val decoded = requireNotNull(decoders.equipment("cable", encoded + updatedAt()))
        assertNull(decoded.microOptionsJson)
    }

    @Test
    @DisplayName("lift encode/decode preserves the null versus empty distinction")
    fun liftNullVersusEmpty() {
        val inherited =
            LiftEntity(
                id = LiftId("a"),
                name = "A",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = "",
                isCustom = true,
                enabled = true,
            )
        val explicitNone = inherited.copy(id = LiftId("b"), microOptionsJson = "[]")

        val inheritedWire = encoders.lift(inherited)["micro_options"]
        val explicitNoneWire = encoders.lift(explicitNone)["micro_options"]
        assertNull(inheritedWire)
        assertEquals(emptyList<Double>(), explicitNoneWire)

        val inheritedBack =
            requireNotNull(decoders.lift("a", encoders.lift(inherited) + updatedAt()))
        val explicitNoneBack =
            requireNotNull(decoders.lift("b", encoders.lift(explicitNone) + updatedAt()))
        assertNull(inheritedBack.microOptionsJson)
        assertEquals("[]", explicitNoneBack.microOptionsJson)
    }

    @Test
    @DisplayName("lift stops survive the full-document round-trip")
    fun liftStopsRoundTrip() {
        val entity =
            LiftEntity(
                id = LiftId("c"),
                name = "C",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = "",
                isCustom = true,
                enabled = true,
                microOptionsJson = "[2.5]",
            )
        val decoded =
            requireNotNull(decoders.lift("c", encoders.lift(entity) + updatedAt()))
        assertEquals("[2.5]", decoded.microOptionsJson)
    }

    @Test
    @DisplayName("corrupt column encodes null so it never wedges sync")
    fun corruptColumnEncodesNull() {
        val entity =
            EquipmentEntity(
                id = EquipmentId("pin"),
                name = "Pin",
                isCustom = true,
                microOptionsJson = "nope",
            )
        assertNull(encoders.equipment(entity)["micro_options"])
    }

    @Test
    @DisplayName("wrong-type wire value decodes null instead of failing the document")
    fun wrongTypeWireDecodesNull() {
        val decoded =
            requireNotNull(
                decoders.lift(
                    "d",
                    mapOf(
                        "name" to "D",
                        "micro_options" to "5.0",
                    ) + updatedAt(),
                ),
            )
        assertNull(decoded.microOptionsJson)
    }

    @Test
    @DisplayName("wire longs decode as stops")
    fun wireLongsDecode() {
        val decoded =
            requireNotNull(
                decoders.equipment(
                    "pin",
                    mapOf(
                        "name" to "Pin",
                        "micro_options" to listOf(5L, 10L),
                    ) + updatedAt(),
                ),
            )
        assertEquals("[5.0,10.0]", decoded.microOptionsJson)
    }

    private fun updatedAt(): Map<String, Any?> =
        mapOf(FirestoreSyncMetadata.UPDATED_AT to 1L)
}
