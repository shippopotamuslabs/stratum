package com.stratum.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Stratum Tonal family — the Material theme family.
 *
 * Accent, border, and text tokens follow the Shippopotamus Palette Gospel (§11 of
 * `contracts/UI_DESIGN.md`), per Sean's 2026-10-19 ratification (STR-138): the steel
 * accent was replaced by the gospel indigo family, and the light spec adopts the Light
 * Palette Gospel (PALETTE-LIGHT) tokens. Dark surface values and the semantic hues
 * (error / setComplete / prHighlight / warning) are unchanged — see §11.3 for the
 * standing differences that remain.
 */
object StratumTonalSpec {
    // Cached singletons: specs are immutable token bundles, and the haze styles
    // they carry are identity-compared by glass modifiers — one instance per
    // family/darkness keeps every consumer on the same style objects.
    private val darkInstance: StratumThemeSpec by lazy { darkSpec() }
    private val lightInstance: StratumThemeSpec by lazy { lightSpec() }

    fun resolve(dark: Boolean): StratumThemeSpec =
        if (dark) {
            darkInstance
        } else {
            lightInstance
        }

    private fun darkSpec(): StratumThemeSpec =
        StratumThemeSpec(
            id = StratumThemeId.Tonal,
            dark = true,
            colors =
                StratumColors(
                    accent = GospelPaletteTokens.Dark.Accent,
                    onAccent = GospelPaletteTokens.Dark.OnAccent,
                    accentMuted = GospelPaletteTokens.Dark.AccentMuted,
                    surfaceBase = GospelPaletteTokens.Dark.SurfaceBase,
                    surfaceRaised = GospelPaletteTokens.Dark.SurfaceRaised,
                    surfaceInset = GospelPaletteTokens.Dark.SurfaceInset,
                    surfaceSunken = GospelPaletteTokens.Dark.SurfaceSunken,
                    borderStrong = GospelPaletteTokens.Dark.BorderStrong,
                    borderSubtle = GospelPaletteTokens.Dark.BorderSubtle,
                    borderInteractive = GospelPaletteTokens.Dark.TextFaint,
                    onAccentMuted = GospelPaletteTokens.Dark.OnAccentMuted,
                    textPrimary = GospelPaletteTokens.Dark.TextPrimary,
                    textMuted = GospelPaletteTokens.Dark.TextMuted,
                    textFaint = GospelPaletteTokens.Dark.TextFaint,
                    textData = GospelPaletteTokens.Dark.TextData,
                    error = GospelPaletteTokens.Dark.Error,
                    onError = GospelPaletteTokens.Dark.OnError,
                    setComplete = GospelPaletteTokens.Dark.SetComplete,
                    prHighlight = GospelPaletteTokens.Dark.PrHighlight,
                    warning = GospelPaletteTokens.Dark.Warning,
                ),
            shapes = tonalShapes(),
            elevations =
                StratumElevations(
                    cardShadowOffset = 0.dp,
                    buttonShadowOffset = 0.dp,
                    useHardShadow = false,
                    hardShadowColor = Color.Transparent,
                ),
            typography = DefaultStratumTypography,
        )

    private fun lightSpec(): StratumThemeSpec =
        StratumThemeSpec(
            id = StratumThemeId.Tonal,
            dark = false,
            colors =
                StratumColors(
                    accent = GospelPaletteTokens.Light.Accent,
                    onAccent = GospelPaletteTokens.Light.OnAccent,
                    accentMuted = GospelPaletteTokens.Light.AccentMuted,
                    surfaceBase = GospelPaletteTokens.Light.SurfaceBase,
                    surfaceRaised = GospelPaletteTokens.Light.SurfaceRaised,
                    surfaceInset = GospelPaletteTokens.Light.SurfaceInset,
                    surfaceSunken = GospelPaletteTokens.Light.SurfaceSunken,
                    borderStrong = GospelPaletteTokens.Light.BorderStrong,
                    borderSubtle = GospelPaletteTokens.Light.BorderSubtle,
                    borderInteractive = GospelPaletteTokens.Light.TextFaint,
                    onAccentMuted = GospelPaletteTokens.Light.OnAccentMuted,
                    textPrimary = GospelPaletteTokens.Light.TextPrimary,
                    textMuted = GospelPaletteTokens.Light.TextMuted,
                    textFaint = GospelPaletteTokens.Light.TextFaint,
                    textData = GospelPaletteTokens.Light.TextData,
                    error = GospelPaletteTokens.Light.Error,
                    onError = GospelPaletteTokens.Light.OnError,
                    setComplete = GospelPaletteTokens.Light.SetComplete,
                    prHighlight = GospelPaletteTokens.Light.PrHighlight,
                    warning = GospelPaletteTokens.Light.Warning,
                ),
            shapes = tonalShapes(),
            elevations =
                StratumElevations(
                    cardShadowOffset = 0.dp,
                    buttonShadowOffset = 0.dp,
                    useHardShadow = false,
                    hardShadowColor = Color.Transparent,
                ),
            typography = DefaultStratumTypography,
        )
}
