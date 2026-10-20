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
 * Pins the STR-138 gospel ratification plus the STR-140 dark surface ramp and
 * semantic hues: Stratum Tonal (the Material family) follows the Shippopotamus
 * Palette + Light Palette gospels; Neo Brutalist is a sanctioned carve-out and keeps
 * its own tokens.
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
    fun tonalDark_resolvesNamedGospelTokens() {
        val colors = StratumTonalSpec.resolve(dark = true).colors
        assertEquals(GospelPaletteTokens.Dark.Accent, colors.accent)
        assertEquals(GospelPaletteTokens.Dark.OnAccent, colors.onAccent)
        assertEquals(GospelPaletteTokens.Dark.AccentMuted, colors.accentMuted)
        assertEquals(GospelPaletteTokens.Dark.SurfaceBase, colors.surfaceBase)
        assertEquals(GospelPaletteTokens.Dark.SurfaceRaised, colors.surfaceRaised)
        assertEquals(GospelPaletteTokens.Dark.SurfaceInset, colors.surfaceInset)
        assertEquals(GospelPaletteTokens.Dark.SurfaceSunken, colors.surfaceSunken)
        assertEquals(GospelPaletteTokens.Dark.BorderStrong, colors.borderStrong)
        assertEquals(GospelPaletteTokens.Dark.BorderSubtle, colors.borderSubtle)
        assertEquals(GospelPaletteTokens.Dark.TextPrimary, colors.textPrimary)
        assertEquals(GospelPaletteTokens.Dark.TextMuted, colors.textMuted)
        assertEquals(GospelPaletteTokens.Dark.TextData, colors.textData)
        assertEquals(GospelPaletteTokens.Dark.Error, colors.error)
        assertEquals(GospelPaletteTokens.Dark.OnError, colors.onError)
        assertEquals(GospelPaletteTokens.Dark.SetComplete, colors.setComplete)
        assertEquals(GospelPaletteTokens.Dark.PrHighlight, colors.prHighlight)
        assertEquals(GospelPaletteTokens.Dark.Warning, colors.warning)
    }

    @Test
    fun tonalLight_resolvesNamedGospelTokens() {
        val colors = StratumTonalSpec.resolve(dark = false).colors
        assertEquals(GospelPaletteTokens.Light.Accent, colors.accent)
        assertEquals(GospelPaletteTokens.Light.OnAccent, colors.onAccent)
        assertEquals(GospelPaletteTokens.Light.AccentMuted, colors.accentMuted)
        assertEquals(GospelPaletteTokens.Light.SurfaceBase, colors.surfaceBase)
        assertEquals(GospelPaletteTokens.Light.SurfaceRaised, colors.surfaceRaised)
        assertEquals(GospelPaletteTokens.Light.SurfaceInset, colors.surfaceInset)
        assertEquals(GospelPaletteTokens.Light.SurfaceSunken, colors.surfaceSunken)
        assertEquals(GospelPaletteTokens.Light.BorderStrong, colors.borderStrong)
        assertEquals(GospelPaletteTokens.Light.BorderSubtle, colors.borderSubtle)
        assertEquals(GospelPaletteTokens.Light.TextPrimary, colors.textPrimary)
        assertEquals(GospelPaletteTokens.Light.TextMuted, colors.textMuted)
        assertEquals(GospelPaletteTokens.Light.TextData, colors.textData)
        assertEquals(GospelPaletteTokens.Light.Error, colors.error)
        assertEquals(GospelPaletteTokens.Light.OnError, colors.onError)
        assertEquals(GospelPaletteTokens.Light.SetComplete, colors.setComplete)
        assertEquals(GospelPaletteTokens.Light.PrHighlight, colors.prHighlight)
        assertEquals(GospelPaletteTokens.Light.Warning, colors.warning)
    }

    @Test
    fun gospelTokens_pinCanonHexPerFamily() {
        // Raw-hex spot checks pin the canon so drift is caught; full
        // per-role pins live above.
        assertEquals(Color(0xFF7A7CFF), GospelPaletteTokens.Dark.Accent)
        assertEquals(Color(0xFF5457E8), GospelPaletteTokens.Light.Accent)
        assertEquals(Color(0xFF0D0E11), GospelPaletteTokens.Dark.SurfaceBase)
        assertEquals(Color(0xFF181A20), GospelPaletteTokens.Dark.SurfaceRaised)
        assertEquals(Color(0xFF272A33), GospelPaletteTokens.Dark.SurfaceInset)
        assertEquals(Color(0xFF111216), GospelPaletteTokens.Dark.SurfaceSunken)
        assertEquals(Color(0xFFE07A7A), GospelPaletteTokens.Dark.Error)
        assertEquals(Color(0xFF0D0E11), GospelPaletteTokens.Dark.OnError)
        assertEquals(Color(0xFF364B40), GospelPaletteTokens.Dark.SetComplete)
        assertEquals(Color(0xFFD9B07A), GospelPaletteTokens.Dark.Warning)
        assertEquals(Color(0xFFC02E3A), GospelPaletteTokens.Light.Error)
        assertEquals(Color(0xFFE6EFE8), GospelPaletteTokens.Light.SetComplete)
        assertEquals(Color(0xFF9C6B10), GospelPaletteTokens.Light.Warning)
    }

    @Test
    fun tonalDark_usesGospelDarkSurfaceRamp() {
        val colors = StratumTonalSpec.resolve(dark = true).colors
        // §11.2 layers: bg-base / surface-1 / surface-3 / bg-subtle.
        assertEquals(Color(0xFF0D0E11), colors.surfaceBase)
        assertEquals(Color(0xFF181A20), colors.surfaceRaised)
        assertEquals(Color(0xFF272A33), colors.surfaceInset)
        assertEquals(Color(0xFF111216), colors.surfaceSunken)
    }

    @Test
    fun tonalDark_usesGospelSemanticHues() {
        val colors = StratumTonalSpec.resolve(dark = true).colors
        assertEquals(Color(0xFFE07A7A), colors.error)
        // onError stays bg-base ink: white on danger-soft-red is only ~3:1.
        assertEquals(Color(0xFF0D0E11), colors.onError)
        assertEquals(Color(0xFFD9B07A), colors.warning)
        // setComplete REMAINS A FILL: success #7DBE8C mixed 30/70 toward
        // surface-1 #181A20.
        assertEquals(Color(0xFF364B40), colors.setComplete)
        // PR gold keeps the saturated family, not warning-amber.
        assertEquals(Color(0xFFE0B040), colors.prHighlight)
    }

    @Test
    fun tonalLight_usesGospelSemanticHues() {
        val colors = StratumTonalSpec.resolve(dark = false).colors
        assertEquals(Color(0xFFC02E3A), colors.error)
        assertEquals(Color(0xFFFFFFFF), colors.onError)
        assertEquals(Color(0xFF9C6B10), colors.warning)
        // 12% tint of success #2E7D43 on white.
        assertEquals(Color(0xFFE6EFE8), colors.setComplete)
        assertEquals(Color(0xFF8A6708), colors.prHighlight)
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
        // Delegated accessors expose the composed M3 scale with zero copies.
        assertEquals(typography.material.bodyLarge, typography.bodyLarge)
        assertEquals(typography.material.displayLarge, typography.displayLarge)
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
