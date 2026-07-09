package com.stratum.app.theme

import com.stratum.core.ui.theme.ColorMode as UiColorMode
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.core.ui.theme.ThemePreferences as UiThemePreferences
import com.stratum.domain.theme.ColorMode as DomainColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences as DomainThemePreferences

/** Maps persisted domain preferences to composition [UiThemePreferences]. */
fun DomainThemePreferences.toUi(): UiThemePreferences =
    UiThemePreferences(
        family = family.toStratumThemeId(),
        colorMode = colorMode.toUiColorMode(),
    )

fun ThemeFamily.toStratumThemeId(): StratumThemeId =
    when (this) {
        ThemeFamily.Tonal -> StratumThemeId.Tonal
        ThemeFamily.NeoBrutalist -> StratumThemeId.NeoBrutalist
    }

fun DomainColorMode.toUiColorMode(): UiColorMode =
    when (this) {
        DomainColorMode.System -> UiColorMode.System
        DomainColorMode.Light -> UiColorMode.Light
        DomainColorMode.Dark -> UiColorMode.Dark
    }
