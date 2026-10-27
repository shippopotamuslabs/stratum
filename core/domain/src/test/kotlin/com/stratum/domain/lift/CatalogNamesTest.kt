package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CatalogNamesTest {

    @Test
    @DisplayName("Equipment name is trimmed by the one name rule")
    fun equipmentNameTrims() {
        val result = CatalogNames.validateEquipmentName("  Cable Stack  ")
        assertEquals(CatalogNames.Validation.Valid("Cable Stack"), result)
        assertEquals("Cable Stack", CatalogNames.equipmentNameResult("  Cable Stack  ").getOrThrow())
    }

    @Test
    @DisplayName("Blank equipment and lift names fail with their user-facing messages")
    fun blankNamesFail() {
        assertEquals(
            CatalogNames.Validation.Invalid(EQUIPMENT_NAME_REQUIRED_MESSAGE),
            CatalogNames.validateEquipmentName("   "),
        )
        assertEquals(
            CatalogNames.Validation.Invalid(LIFT_NAME_REQUIRED_MESSAGE),
            CatalogNames.validateLiftName(""),
        )
        assertEquals(
            EQUIPMENT_NAME_REQUIRED_MESSAGE,
            CatalogNames.equipmentNameResult(null).exceptionOrNull()?.message,
        )
    }

    @Test
    @DisplayName("Untouched equipment draft has no message; touched-but-blank draft does")
    fun draftMessageSemantics() {
        assertNull(CatalogNames.equipmentDraftValidationMessage(null))
        assertNull(CatalogNames.equipmentDraftValidationMessage(" Plates "))
        assertEquals(
            EQUIPMENT_NAME_REQUIRED_MESSAGE,
            CatalogNames.equipmentDraftValidationMessage("  "),
        )
    }

    @Test
    @DisplayName("Optional equipment name: blank means absent, otherwise trimmed")
    fun optionalEquipmentName() {
        assertNull(CatalogNames.normalizedEquipmentNameOrNull("   "))
        assertNull(CatalogNames.normalizedEquipmentNameOrNull(null))
        assertEquals("landmine", CatalogNames.normalizedEquipmentNameOrNull("  landmine  "))
        assertTrue(CatalogNames.liftNameResult("  Press ").isSuccess)
    }
}
