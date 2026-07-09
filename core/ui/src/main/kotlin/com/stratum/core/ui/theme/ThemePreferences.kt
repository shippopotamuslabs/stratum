package com.stratum.core.ui.theme

/**
 * Composition-side theme selection. Mirrors domain
 * [com.stratum.domain.theme.ThemePreferences] for the app shell — UI never imports domain
 * models into composables; the shell maps domain → this type before [StratumTheme].
 *
 * See contracts/UI_DESIGN.md §9.
 */
data class ThemePreferences(
    val family: StratumThemeId = StratumThemeId.Tonal,
    val colorMode: ColorMode = ColorMode.System,
)

/**
 * Resolves whether dark tokens should apply for the given [ColorMode].
 *
 * @param isSystemDark current system dark flag from [androidx.compose.foundation.isSystemInDarkTheme]
 */
fun ColorMode.resolveDark(isSystemDark: Boolean): Boolean =
    when (this) {
        ColorMode.System -> isSystemDark
        ColorMode.Light -> false
        ColorMode.Dark -> true
    }
