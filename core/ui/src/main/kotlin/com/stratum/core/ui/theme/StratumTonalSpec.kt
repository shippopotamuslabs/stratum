package com.stratum.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SteelAccent = Color(0xFF5B7C99)

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
                    accent = SteelAccent,
                    onAccent = Color(0xFFFFFFFF),
                    accentMuted = Color(0xFF3D5A72),
                    surfaceBase = Color(0xFF121212),
                    surfaceRaised = Color(0xFF1E1E1E),
                    surfaceInset = Color(0xFF282828),
                    surfaceSunken = Color(0xFF0E0E0E),
                    borderStrong = Color(0xFF4A4A4A),
                    borderSubtle = Color(0xFF3A3A3A),
                    textPrimary = Color(0xFFF2F2F2),
                    textMuted = Color(0xFF9CA3AF),
                    textData = Color(0xFFE5E7EB),
                    error = Color(0xFFEF4444),
                    onError = Color(0xFFFFFFFF),
                    setComplete = Color(0xFF1B3D2F),
                    prHighlight = Color(0xFFE0B040),
                ),
            shapes = tonalShapes(),
            elevations =
                StratumElevations(
                    cardShadowOffset = 0.dp,
                    buttonShadowOffset = 0.dp,
                    useHardShadow = false,
                ),
            typography = DefaultStratumTypography,
        )

    private fun lightSpec(): StratumThemeSpec =
        StratumThemeSpec(
            id = StratumThemeId.Tonal,
            dark = false,
            colors =
                StratumColors(
                    accent = SteelAccent,
                    onAccent = Color(0xFFFFFFFF),
                    accentMuted = Color(0xFF4A657A),
                    surfaceBase = Color(0xFFF4F4F4),
                    surfaceRaised = Color(0xFFFFFFFF),
                    surfaceInset = Color(0xFFF4F4F4),
                    surfaceSunken = Color(0xFFE8E8E8),
                    borderStrong = Color(0xFFCBD5E1),
                    borderSubtle = Color(0xFFE2E8F0),
                    textPrimary = Color(0xFF111827),
                    textMuted = Color(0xFF475569),
                    textData = Color(0xFF1F2937),
                    error = Color(0xFFDC2626),
                    onError = Color(0xFFFFFFFF),
                    setComplete = Color(0xFFE8F5E9),
                    prHighlight = Color(0xFF8A6708),
                ),
            shapes = tonalShapes(),
            elevations =
                StratumElevations(
                    cardShadowOffset = 0.dp,
                    buttonShadowOffset = 0.dp,
                    useHardShadow = false,
                ),
            typography = DefaultStratumTypography,
        )
}
