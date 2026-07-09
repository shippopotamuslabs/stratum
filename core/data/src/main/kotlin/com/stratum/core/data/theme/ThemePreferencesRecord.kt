package com.stratum.core.data.theme

import kotlinx.serialization.Serializable

/**
 * Typed DataStore root for [com.stratum.domain.theme.ThemePreferences].
 * Enum names match domain enums so mapping stays name-based.
 */
@Serializable
data class ThemePreferencesRecord(
    val family: String = ThemeFamilyNames.TONAL,
    val colorMode: String = ColorModeNames.SYSTEM,
)

internal object ThemeFamilyNames {
    const val TONAL = "Tonal"
    const val NEO_BRUTALIST = "NeoBrutalist"
}

internal object ColorModeNames {
    const val SYSTEM = "System"
    const val LIGHT = "Light"
    const val DARK = "Dark"
}
