package com.stratum.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StratumElevationsSpecTest {

    @Test
    fun neoDark_usesHardShadowWithLargerButtonOffset() {
        val elevations = NeoBrutalistSpec.resolve(dark = true).elevations
        assertTrue(elevations.useHardShadow)
        assertEquals(5.dp, elevations.buttonShadowOffset)
        assertEquals(4.dp, elevations.cardShadowOffset)
        assertEquals(Color(0xFF000000), elevations.hardShadowColor)
    }

    @Test
    fun neoLight_usesSmallerButtonOffsetThanDark() {
        val dark = NeoBrutalistSpec.resolve(dark = true).elevations
        val light = NeoBrutalistSpec.resolve(dark = false).elevations
        assertTrue(light.useHardShadow)
        assertEquals(2.dp, light.buttonShadowOffset)
        assertEquals(3.dp, light.cardShadowOffset)
        assertTrue(light.buttonShadowOffset < dark.buttonShadowOffset)
        assertEquals(Color(0xFF0F172A), light.hardShadowColor)
    }

    @Test
    fun tonal_disablesHardShadow() {
        val dark = StratumTonalSpec.resolve(dark = true).elevations
        val light = StratumTonalSpec.resolve(dark = false).elevations
        assertFalse(dark.useHardShadow)
        assertFalse(light.useHardShadow)
        assertEquals(0.dp, dark.buttonShadowOffset)
        assertEquals(0.dp, light.buttonShadowOffset)
        assertEquals(Color.Transparent, dark.hardShadowColor)
        assertEquals(Color.Transparent, light.hardShadowColor)
    }

    @Test
    fun factory_routesFamilyToMatchingElevations() {
        val neo = StratumThemeSpecFactory.resolve(StratumThemeId.NeoBrutalist, dark = true)
        val tonal = StratumThemeSpecFactory.resolve(StratumThemeId.Tonal, dark = true)
        assertTrue(neo.elevations.useHardShadow)
        assertFalse(tonal.elevations.useHardShadow)
    }
}
