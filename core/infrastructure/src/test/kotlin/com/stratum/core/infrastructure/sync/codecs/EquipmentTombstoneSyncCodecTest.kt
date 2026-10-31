package com.stratum.core.infrastructure.sync.codecs

import com.stratum.core.infrastructure.db.EquipmentEntity
import com.stratum.domain.lift.identity.EquipmentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EquipmentTombstoneSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    @Test
    @DisplayName("equipment tombstone flag round-trips through sync")
    fun tombstoneFlagRoundTrip() {
        val tombstoned = equipmentEntity(isDeleted = true)

        val encoded = encoders.equipment(tombstoned)
        assertEquals(true, encoded["is_deleted"])

        val decoded = requireNotNull(decoders.equipment("eq-1", encoded))
        assertTrue(decoded.isDeleted)
    }

    @Test
    @DisplayName("equipment live flag round-trips through sync")
    fun liveFlagRoundTrip() {
        val encoded = encoders.equipment(equipmentEntity(isDeleted = false))
        assertEquals(false, encoded["is_deleted"])

        val decoded = requireNotNull(decoders.equipment("eq-1", encoded))
        assertFalse(decoded.isDeleted)
    }

    @Test
    @DisplayName("equipment decodes a missing is_deleted as live (pre-tombstone cloud docs)")
    fun missingFlagDecodesLive() {
        val decoded =
            requireNotNull(
                decoders.equipment(
                    "eq-1",
                    mapOf("name" to "Cables", "is_custom" to false),
                ),
            )

        assertFalse(decoded.isDeleted)
    }

    @Test
    @DisplayName("seed-shaped tombstone (is_custom false) round-trips through sync")
    fun seedTombstoneRoundTrip() {
        val tombstoned =
            EquipmentEntity(
                id = EquipmentId("cable"),
                name = "Cables",
                isCustom = false,
                isDeleted = true,
            )

        val encoded = encoders.equipment(tombstoned)
        assertEquals(false, encoded["is_custom"])
        assertEquals(true, encoded["is_deleted"])

        val decoded = requireNotNull(decoders.equipment("cable", encoded))
        assertFalse(decoded.isCustom)
        assertTrue(decoded.isDeleted)
    }

    private fun equipmentEntity(isDeleted: Boolean): EquipmentEntity =
        EquipmentEntity(
            id = EquipmentId("eq-1"),
            name = "Cables",
            isCustom = true,
            isDeleted = isDeleted,
        )
}
