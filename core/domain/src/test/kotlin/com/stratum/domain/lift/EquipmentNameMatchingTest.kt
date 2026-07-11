package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EquipmentNameMatchingTest {

    @Test
    @DisplayName("normalizeKey collapses cable vs Cables")
    fun cablePluralMatches() {
        assertEquals(
            EquipmentNameMatching.normalizeKey("cable"),
            EquipmentNameMatching.normalizeKey("Cables"),
        )
    }

    @Test
    @DisplayName("seedIdFromFreeText maps cable shorthand to seed id")
    fun cableAlias() {
        assertEquals(EquipmentId("cable"), EquipmentNameMatching.seedIdFromFreeText("cable"))
        assertEquals(EquipmentId("cable"), EquipmentNameMatching.seedIdFromFreeText("  Cables  "))
    }

    @Test
    @DisplayName("seedIdFromFreeText maps dumbbells to seed id")
    fun dumbbellAlias() {
        assertEquals(EquipmentId("dumbbell"), EquipmentNameMatching.seedIdFromFreeText("dumbbells"))
    }

    @Test
    @DisplayName("unknown free-text returns null so callers can create custom equipment")
    fun unknownReturnsNull() {
        assertNull(EquipmentNameMatching.seedIdFromFreeText("Life Fitness Pro2 Stack"))
    }
}
