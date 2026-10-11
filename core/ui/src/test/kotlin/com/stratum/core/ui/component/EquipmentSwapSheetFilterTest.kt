package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EquipmentSwapSheetFilterTest {
    private val options =
        listOf(
            EquipmentSwapOption(id = "barbell", name = "Olympic Barbell"),
            EquipmentSwapOption(id = "ssb", name = "Safety Squat Bar"),
            EquipmentSwapOption(id = "cables", name = "Cables"),
        )

    @Test
    @DisplayName("blank query returns the full list")
    fun blankQueryMatchesAll() {
        assertEquals(options, filterEquipmentOptions("", options))
        assertEquals(options, filterEquipmentOptions("   ", options))
    }

    @Test
    @DisplayName("query matches case-insensitively on name substring")
    fun caseInsensitiveSubstring() {
        val matches = filterEquipmentOptions("bar", options)
        assertEquals(listOf(options[0], options[1]), matches)
        assertEquals(listOf(options[2]), filterEquipmentOptions("ABLE", options))
    }

    @Test
    @DisplayName("query is trimmed before matching")
    fun queryTrimmed() {
        assertEquals(listOf(options[2]), filterEquipmentOptions("  cables  ", options))
    }

    @Test
    @DisplayName("no match returns an empty list")
    fun noMatchEmpty() {
        assertEquals(emptyList<EquipmentSwapOption>(), filterEquipmentOptions("kettlebell", options))
    }
}
