package com.stratum.core.data.sync.codecs

import com.stratum.core.data.db.LiftEntity
import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiftTombstoneSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    @Test
    @DisplayName("lift tombstone flag round-trips through sync (full document)")
    fun tombstoneFlagRoundTrip() {
        val encoded = encoders.lift(liftEntity(isDeleted = true))
        assertEquals(true, encoded["is_deleted"])

        val decoded = requireNotNull(decoders.lift("lift-1", encoded))
        assertTrue(decoded.isDeleted)
    }

    @Test
    @DisplayName("lift live flag round-trips through sync")
    fun liveFlagRoundTrip() {
        val encoded = encoders.lift(liftEntity(isDeleted = false))
        assertEquals(false, encoded["is_deleted"])

        val decoded = requireNotNull(decoders.lift("lift-1", encoded))
        assertFalse(decoded.isDeleted)
    }

    @Test
    @DisplayName("lift decodes a missing is_deleted as live (pre-tombstone cloud docs)")
    fun missingFlagDecodesLive() {
        val decoded =
            requireNotNull(
                decoders.lift(
                    "lift-1",
                    mapOf("name" to "Bench Press", "is_custom" to true),
                ),
            )

        assertFalse(decoded.isDeleted)
    }

    private fun liftEntity(isDeleted: Boolean): LiftEntity =
        LiftEntity(
            id = LiftId("lift-1"),
            name = "Bench Press",
            movementPatternsBitmask = 0,
            primaryMusclesBitmask = 0,
            secondaryMusclesBitmask = 0,
            presetTags = ",",
            isCustom = true,
            enabled = true,
            isDeleted = isDeleted,
        )
}
