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
    /**
     * Interactive boundary (WCAG 1.4.11 3:1 non-text bar): the border role for
     * outlined controls, not a hairline. borderSubtle (#2A2E39: 1.06:1 on the
     * sheet surfaceInset #272A33, 1.42:1 on bg-base) and dark borderStrong
     * (1.26:1 on sheets) both fail that bar, so in Tonal this resolves to the
     * text-faint ramp step (dark #6E7280, light #83868F). Consume this token —
     * never the text ramp — so text-ramp edits cannot silently recolor borders.
     */
    val borderInteractive: Color,
    val textPrimary: Color,
    val textMuted: Color,
    /**
     * Faintest ink (palette text-faint). Non-text grade (~3.3:1): incidental UI
     * only, such as the bottom-sheet drag handle; never body text.
     */
    val textFaint: Color,
    val textData: Color,
    val error: Color,
    val onError: Color,
    val setComplete: Color,
    val prHighlight: Color,
    /** Non-blocking warnings (e.g. increments that miss logged weights) — amber. */
    val warning: Color,
)
