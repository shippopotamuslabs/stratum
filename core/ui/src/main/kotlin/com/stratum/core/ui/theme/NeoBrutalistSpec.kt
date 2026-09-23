package com.stratum.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object NeoBrutalistSpec {
    fun resolve(dark: Boolean): StratumThemeSpec =
        if (dark) {
            darkSpec()
        } else {
            lightSpec()
        }

    private fun darkSpec(): StratumThemeSpec =
        StratumThemeSpec(
            id = StratumThemeId.NeoBrutalist,
            dark = true,
            colors =
                StratumColors(
                    accent = Color(0xFFC45C26),
                    onAccent = Color(0xFF1A1A1A),
                    accentMuted = Color(0xFF9A4A22),
                    surfaceBase = Color(0xFF1A1A1A),
                    surfaceRaised = Color(0xFF242424),
                    surfaceInset = Color(0xFF2E2E2E),
                    surfaceSunken = Color(0xFF121212),
                    borderStrong = Color(0xFFA3A3A3),
                    borderSubtle = Color(0xFF3D3D3D),
                    textPrimary = Color(0xFFF5F5F5),
                    textMuted = Color(0xFFA3A3A3),
                    textData = Color(0xFFE5E5E5),
                    error = Color(0xFFEF4444),
                    onError = Color(0xFF1A1A1A),
                    setComplete = Color(0xFF14532D),
                    prHighlight = Color(0xFFE0B040),
                    warning = Color(0xFFFBBF24),
                ),
            shapes = neoBrutalistShapes(),
            elevations =
                StratumElevations(
                    // Larger dark offsets so hard edges read against charcoal (pocket-crew parity).
                    cardShadowOffset = 4.dp,
                    buttonShadowOffset = 5.dp,
                    useHardShadow = true,
                    hardShadowColor = Color(0xFF000000),
                ),
            typography = DefaultStratumTypography,
        )

    private fun lightSpec(): StratumThemeSpec =
        StratumThemeSpec(
            id = StratumThemeId.NeoBrutalist,
            dark = false,
            colors =
                StratumColors(
                    accent = Color(0xFFB45309),
                    onAccent = Color(0xFF1A1A1A),
                    accentMuted = Color(0xFF92400E),
                    surfaceBase = Color(0xFFE8E8E8),
                    surfaceRaised = Color(0xFFF4F4F4),
                    surfaceInset = Color(0xFFEBEBEB),
                    surfaceSunken = Color(0xFFD4D4D4),
                    borderStrong = Color(0xFF1A1A1A),
                    borderSubtle = Color(0xFF525252),
                    textPrimary = Color(0xFF171717),
                    textMuted = Color(0xFF525252),
                    textData = Color(0xFF262626),
                    error = Color(0xFFDC2626),
                    onError = Color(0xFFFFFFFF),
                    setComplete = Color(0xFFDCFCE7),
                    prHighlight = Color(0xFF8A6708),
                    warning = Color(0xFFB45309),
                ),
            shapes = neoBrutalistShapes(),
            elevations =
                StratumElevations(
                    cardShadowOffset = 3.dp,
                    buttonShadowOffset = 2.dp,
                    useHardShadow = true,
                    // Near-black slate so the offset reads on light concrete without pure pure-black harshness.
                    hardShadowColor = Color(0xFF0F172A),
                ),
            typography = DefaultStratumTypography,
        )
}
