package com.stratum.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Pins the STR-138 gospel ratification: Stratum Tonal (the Material family) follows the
 * Shippopotamus Palette + Light Palette gospels; Neo Brutalist is a sanctioned carve-out
 * and keeps its own tokens.
 */
class StratumGospelTokensTest {

    @Test
    fun tonalDark_usesGospelIndigoAccentFamily() {
        val colors = StratumTonalSpec.resolve(dark = true).colors
        assertEquals(Color(0xFF7A7CFF), colors.accent)
        assertEquals(Color(0xFF0D0E11), colors.onAccent)
        assertEquals(Color(0xFF3B3BAA), colors.accentMuted)
    }

    @Test
    fun tonalDark_usesGospelBorderAndTextRamps() {
        val colors = StratumTonalSpec.resolve(dark = true).colors
        assertEquals(Color(0xFF343A48), colors.borderStrong)
        assertEquals(Color(0xFF2A2E39), colors.borderSubtle)
        assertEquals(Color(0xFFEDEEF0), colors.textPrimary)
        assertEquals(Color(0xFFA1A5B0), colors.textMuted)
        assertEquals(Color(0xFFEDEEF0), colors.textData)
    }

    @Test
    fun tonalLight_adoptsLightGospelTokens() {
        val colors = StratumTonalSpec.resolve(dark = false).colors
        assertEquals(Color(0xFF5457E8), colors.accent)
        assertEquals(Color(0xFFFFFFFF), colors.onAccent)
        assertEquals(Color(0xFF2F31A0), colors.accentMuted)
        // White cards on a grey canvas.
        assertEquals(Color(0xFFF4F4F8), colors.surfaceBase)
        assertEquals(Color(0xFFFFFFFF), colors.surfaceRaised)
        assertEquals(Color(0xFFEFEFF4), colors.surfaceInset)
        assertEquals(Color(0xFFECECF1), colors.surfaceSunken)
        assertEquals(Color(0xFF83858F), colors.borderStrong)
        assertEquals(Color(0xFFC6C7D1), colors.borderSubtle)
        assertEquals(Color(0xFF1B1C22), colors.textPrimary)
        assertEquals(Color(0xFF494B5C), colors.textMuted)
        assertEquals(Color(0xFF1B1C22), colors.textData)
    }

    @Test
    fun tonalButtons_areFullPill() {
        assertEquals(CircleShape, StratumTonalSpec.resolve(dark = true).shapes.button)
        assertEquals(CircleShape, StratumTonalSpec.resolve(dark = false).shapes.button)
    }

    @Test
    fun typography_carriesFullGospelScale() {
        val typography = StratumTonalSpec.resolve(dark = true).typography
        assertEquals(57.sp, typography.displayLarge.fontSize)
        assertEquals(64.sp, typography.displayLarge.lineHeight)
        assertEquals(FontWeight.Medium, typography.titleMedium.fontWeight)
        assertEquals(16.sp, typography.titleMedium.fontSize)
        assertEquals(24.sp, typography.titleMedium.lineHeight)
        assertEquals(14.sp, typography.labelLarge.fontSize)
        assertEquals(FontWeight.Medium, typography.labelLarge.fontWeight)
        assertEquals(14.sp, typography.bodyMedium.fontSize)
        assertEquals(12.sp, typography.bodySmall.fontSize)
        // Stratum-specific styles survive the completion untouched.
        assertEquals(20.sp, typography.dataLarge.fontSize)
        assertEquals(13.sp, typography.sectionLabel.fontSize)
    }

    @Test
    fun neoBrutalist_carveOutKeepsItsOwnTokens() {
        val dark = NeoBrutalistSpec.resolve(dark = true)
        assertEquals(Color(0xFFC45C26), dark.colors.accent)
        assertEquals(4.dp, dark.shapes.panelCorner)
        assertFalse(dark.shapes.button == CircleShape)
        val light = NeoBrutalistSpec.resolve(dark = false)
        assertEquals(Color(0xFFB45309), light.colors.accent)
    }
}
