package com.stratum.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class StratumColors(
    val accent: Color,
    val onAccent: Color,
    val accentMuted: Color,
    /** Content on an [accentMuted] container (tonal selected states, §12.3). */
    val onAccentMuted: Color,
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceInset: Color,
    val surfaceSunken: Color,
    val borderStrong: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textData: Color,
    val error: Color,
    val onError: Color,
    val setComplete: Color,
    val prHighlight: Color,
    /** Non-blocking warnings (e.g. increments that miss logged weights) — amber. */
    val warning: Color,
)
