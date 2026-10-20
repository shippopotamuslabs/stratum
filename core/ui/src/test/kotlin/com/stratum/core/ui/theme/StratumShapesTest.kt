package com.stratum.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the STR-141 gospel shape scale + elevation/tint bridge: Tonal carries the
 * §12.2 scale, Neo mirrors its own 4dp geometry (§11.3 carve-out), the nested
 * helper floors at zero, and the Material bridge derives every added slot from
 * the semantic ramp.
 */
class StratumShapesTest {

    @Test
    fun tonal_carriesGospelShapeScale() {
        val dark = StratumTonalSpec.resolve(dark = true).shapes
        val light = StratumTonalSpec.resolve(dark = false).shapes
        for (shapes in listOf(dark, light)) {
            assertEquals(4.dp, shapes.extraSmallCorner)
            assertEquals(8.dp, shapes.smallCorner)
            assertEquals(12.dp, shapes.mediumCorner)
            assertEquals(16.dp, shapes.largeCorner)
            assertEquals(28.dp, shapes.extraLargeCorner)
            assertEquals(RoundedCornerShape(4.dp), shapes.extraSmall)
            assertEquals(RoundedCornerShape(8.dp), shapes.small)
            assertEquals(RoundedCornerShape(12.dp), shapes.medium)
            assertEquals(RoundedCornerShape(16.dp), shapes.large)
            assertEquals(RoundedCornerShape(28.dp), shapes.extraLarge)
            // Legacy fields keep working on their gospel rungs.
            assertEquals(12.dp, shapes.panelCorner)
            assertEquals(8.dp, shapes.chipCorner)
        }
    }

    @Test
    fun neo_extraScaleMirrorsItsOwn4dpGeometry() {
        val dark = NeoBrutalistSpec.resolve(dark = true).shapes
        val light = NeoBrutalistSpec.resolve(dark = false).shapes
        for (shapes in listOf(dark, light)) {
            assertEquals(4.dp, shapes.extraSmallCorner)
            assertEquals(4.dp, shapes.smallCorner)
            assertEquals(4.dp, shapes.mediumCorner)
            assertEquals(4.dp, shapes.largeCorner)
            assertEquals(4.dp, shapes.extraLargeCorner)
            assertEquals(RoundedCornerShape(4.dp), shapes.extraLarge)
            assertEquals(RoundedCornerShape(4.dp), shapes.large)
            // Legacy Neo identity untouched: no gospel radii in panel/button/chip.
            assertEquals(4.dp, shapes.panelCorner)
            assertEquals(RoundedCornerShape(4.dp), shapes.panel)
            assertEquals(RoundedCornerShape(4.dp), shapes.button)
            assertEquals(RoundedCornerShape(4.dp), shapes.chip)
        }
    }

    @Test
    fun nestedChildRadius_subtractsPadding() {
        // Gospel example: 28dp sheet + 12dp padding -> 16dp inner.
        assertEquals(16.dp, nestedChildRadius(28.dp, 12.dp))
        assertEquals(4.dp, nestedChildRadius(12.dp, 8.dp))
    }

    @Test
    fun nestedChildRadius_floorsAtZero() {
        assertEquals(0.dp, nestedChildRadius(8.dp, 12.dp))
        assertEquals(0.dp, nestedChildRadius(4.dp, 4.dp))
        assertEquals(12.dp, nestedChildRadius(12.dp, 0.dp))
    }

    @Test
    fun materialBridge_tonalDarkMapsContainersAndTint() {
        val spec = StratumTonalSpec.resolve(dark = true)
        val scheme = materialColorScheme(spec)
        assertEquals(spec.colors.accent, scheme.surfaceTint)
        assertEquals(spec.colors.surfaceBase, scheme.surfaceContainerLowest)
        assertEquals(spec.colors.surfaceSunken, scheme.surfaceContainerLow)
        assertEquals(spec.colors.surfaceRaised, scheme.surfaceContainer)
        assertEquals(spec.colors.surfaceRaised, scheme.surfaceContainerHigh)
        assertEquals(spec.colors.surfaceInset, scheme.surfaceContainerHighest)
        // Legacy slots preserved.
        assertEquals(spec.colors.surfaceRaised, scheme.surface)
        assertEquals(spec.colors.surfaceBase, scheme.background)
    }

    @Test
    fun materialBridge_tonalLightMapsContainersAndTint() {
        val spec = StratumTonalSpec.resolve(dark = false)
        val scheme = materialColorScheme(spec)
        assertEquals(spec.colors.accent, scheme.surfaceTint)
        assertEquals(spec.colors.surfaceRaised, scheme.surfaceContainerLowest)
        assertEquals(spec.colors.surfaceBase, scheme.surfaceContainerLow)
        assertEquals(spec.colors.surfaceInset, scheme.surfaceContainer)
        assertEquals(spec.colors.surfaceRaised, scheme.surfaceContainerHigh)
        assertEquals(spec.colors.surfaceRaised, scheme.surfaceContainerHighest)
    }

    @Test
    fun materialBridge_neoUsesOwnRampAndAccentAsTint() {
        val dark = NeoBrutalistSpec.resolve(dark = true)
        val darkScheme = materialColorScheme(dark)
        assertEquals(dark.colors.accent, darkScheme.surfaceTint)
        assertEquals(dark.colors.surfaceBase, darkScheme.surfaceContainerLowest)
        assertEquals(dark.colors.surfaceSunken, darkScheme.surfaceContainerLow)
        assertEquals(dark.colors.surfaceRaised, darkScheme.surfaceContainer)
        assertEquals(dark.colors.surfaceInset, darkScheme.surfaceContainerHighest)
        val light = NeoBrutalistSpec.resolve(dark = false)
        assertEquals(light.colors.accent, materialColorScheme(light).surfaceTint)
    }

    @Test
    fun materialBridge_shapesFollowSpecScale() {
        val tonal = materialShapes(StratumTonalSpec.resolve(dark = true))
        assertEquals(RoundedCornerShape(4.dp), tonal.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), tonal.small)
        assertEquals(RoundedCornerShape(12.dp), tonal.medium)
        assertEquals(RoundedCornerShape(16.dp), tonal.large)
        assertEquals(RoundedCornerShape(28.dp), tonal.extraLarge)
        val neo = materialShapes(NeoBrutalistSpec.resolve(dark = true))
        assertEquals(RoundedCornerShape(4.dp), neo.extraLarge)
        assertEquals(RoundedCornerShape(4.dp), neo.medium)
    }
}
