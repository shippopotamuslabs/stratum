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
    fun resolve(dark: Boolean): StratumThemeSpec =
        if (dark) {
            darkSpec()
        } else {
            lightSpec()
        }

    private fun darkSpec(): StratumThemeSpec =
        StratumThemeSpec(
            id = StratumThemeId.Tonal,
            dark = true,
            colors =
                StratumColors(
                    // Gospel indigo family (§11.2): base / on-accent / deep (muted).
                    // onAccent is bg-base, not white: #FFFFFF on #7A7CFF is only
                    // 3.4:1, while #0D0E11 reaches 5.7:1 (WCAG AA).
                    accent = Color(0xFF7A7CFF),
                    onAccent = Color(0xFF0D0E11),
                    accentMuted = Color(0xFF3B3BAA),
                    surfaceBase = Color(0xFF121212),
                    surfaceRaised = Color(0xFF1E1E1E),
                    surfaceInset = Color(0xFF282828),
                    surfaceSunken = Color(0xFF0E0E0E),
                    // Gospel border ramp: Stratum's two roles map to border-strong /
                    // border-default (gospel border-subtle has no Stratum role).
                    borderStrong = Color(0xFF343A48),
                    borderSubtle = Color(0xFF2A2E39),
                    // Gospel text ramp: primary / secondary; textData has no gospel
                    // counterpart and tracks text-primary ink (tabular figures carry
                    // the data distinction, not hue).
                    textPrimary = Color(0xFFEDEEF0),
                    textMuted = Color(0xFFA1A5B0),
                    textData = Color(0xFFEDEEF0),
                    error = Color(0xFFEF4444),
                    onError = Color(0xFFFFFFFF),
                    setComplete = Color(0xFF1B3D2F),
                    prHighlight = Color(0xFFE0B040),
                    warning = Color(0xFFFBBF24),
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
                    // Light Gospel indigo family (PALETTE-LIGHT): base carries white
                    // text at 5.4:1; muted maps to the family's deep step.
                    accent = Color(0xFF5457E8),
                    onAccent = Color(0xFFFFFFFF),
                    accentMuted = Color(0xFF2F31A0),
                    // Light Gospel layers: grey canvas, white cards, tonal fills,
                    // bg-subtle wells — elevation climbs toward white.
                    surfaceBase = Color(0xFFF4F4F8),
                    surfaceRaised = Color(0xFFFFFFFF),
                    surfaceInset = Color(0xFFEFEFF4),
                    surfaceSunken = Color(0xFFECECF1),
                    // Light Gospel borders: strong meets WCAG 1.4.11 (3:1) for
                    // interactive boundaries; subtle maps to border-default.
                    borderStrong = Color(0xFF83858F),
                    borderSubtle = Color(0xFFC6C7D1),
                    // Light Gospel text ramp: primary (15.5:1) / secondary (AAA);
                    // textData tracks text-primary, as in dark.
                    textPrimary = Color(0xFF1B1C22),
                    textMuted = Color(0xFF494B5C),
                    textData = Color(0xFF1B1C22),
                    error = Color(0xFFDC2626),
                    onError = Color(0xFFFFFFFF),
                    setComplete = Color(0xFFE8F5E9),
                    prHighlight = Color(0xFF8A6708),
                    warning = Color(0xFFB45309),
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
