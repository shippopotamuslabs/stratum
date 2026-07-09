package com.stratum.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.settings.AppearanceUi
import com.stratum.feature.settings.SettingsUiState
import com.stratum.feature.settings.ThemeFamilyUi

@PreviewLightDark
@Composable
private fun SettingsScreenTonalPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = false) {
        SettingsScreen(
            uiState = SettingsUiState(
                appearance = AppearanceUi.System,
                themeFamily = ThemeFamilyUi.Stratum,
            ),
            onBackClick = {},
            onAppearanceSelected = {},
            onThemeFamilySelected = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        SettingsScreen(
            uiState = SettingsUiState(
                appearance = AppearanceUi.Dark,
                themeFamily = ThemeFamilyUi.NeoBrutalist,
            ),
            onBackClick = {},
            onAppearanceSelected = {},
            onThemeFamilySelected = {},
        )
    }
}
