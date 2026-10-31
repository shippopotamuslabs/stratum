package com.stratum.core.infrastructure.theme

import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences

internal fun ThemePreferencesRecord.toDomain(): ThemePreferences =
    ThemePreferences(
        family = family.toThemeFamily(),
        colorMode = colorMode.toColorMode(),
    )

internal fun ThemePreferences.toRecord(): ThemePreferencesRecord =
    ThemePreferencesRecord(
        family = family.toStorageName(),
        colorMode = colorMode.toStorageName(),
    )

private fun String.toThemeFamily(): ThemeFamily =
    when (this) {
        ThemeFamilyNames.NEO_BRUTALIST -> ThemeFamily.NeoBrutalist
        else -> ThemeFamily.Tonal
    }

private fun String.toColorMode(): ColorMode =
    when (this) {
        ColorModeNames.LIGHT -> ColorMode.Light
        ColorModeNames.DARK -> ColorMode.Dark
        else -> ColorMode.System
    }

private fun ThemeFamily.toStorageName(): String =
    when (this) {
        ThemeFamily.Tonal -> ThemeFamilyNames.TONAL
        ThemeFamily.NeoBrutalist -> ThemeFamilyNames.NEO_BRUTALIST
    }

private fun ColorMode.toStorageName(): String =
    when (this) {
        ColorMode.System -> ColorModeNames.SYSTEM
        ColorMode.Light -> ColorModeNames.LIGHT
        ColorMode.Dark -> ColorModeNames.DARK
    }
