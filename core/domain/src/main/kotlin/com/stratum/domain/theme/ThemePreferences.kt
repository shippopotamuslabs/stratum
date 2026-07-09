package com.stratum.domain.theme

/**
 * Persisted user theme selection. Maps to `:core:ui` composition via
 * [com.stratum.core.ui.theme.StratumThemeId] / [com.stratum.core.ui.theme.ColorMode] at the
 * app shell — domain stays free of Compose types.
 *
 * Defaults match [contracts/UI_DESIGN.md] §9 (Tonal + System).
 */
data class ThemePreferences(
    val family: ThemeFamily = ThemeFamily.Tonal,
    val colorMode: ColorMode = ColorMode.System,
)

/** Theme chrome family — Tonal (Stratum default) or Neo Brutalist. */
enum class ThemeFamily {
    Tonal,
    NeoBrutalist,
}

/**
 * Appearance / dark-mode policy.
 *
 * - [System] follows the device light/dark setting (what users often call “Material You”).
 * - [Light] / [Dark] force a fixed mode.
 */
enum class ColorMode {
    System,
    Light,
    Dark,
}
