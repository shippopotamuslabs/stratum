package com.stratum.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.settings.AppearanceUi
import com.stratum.feature.settings.SettingsUiState
import com.stratum.feature.settings.ThemeFamilyUi
import com.stratum.feature.settings.WeightUnitUi

@PreviewLightDark
@Composable
private fun SettingsScreenTonalPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = false) {
        SettingsScreen(
            uiState = SettingsUiState(
                appearance = AppearanceUi.System,
                themeFamily = ThemeFamilyUi.Stratum,
                checkInsEnabled = true,
                equipmentProposalsEnabled = true,
                repRangeProposalsEnabled = false,
                weightUnit = WeightUnitUi.Lbs,
            ),
            onBackClick = {},
            onAppearanceSelected = {},
            onThemeFamilySelected = {},
            onCheckInsEnabledChange = {},
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
                checkInsEnabled = false,
                equipmentProposalsEnabled = true,
                repRangeProposalsEnabled = true,
                weightUnit = WeightUnitUi.Kg,
                healthConnectEnabled = true,
                healthConnectWriteGranted = true,
                healthConnectReadsGranted = false,
                showDebugTools = true,
                debugStatusMessage = "Check-in forced for testing.",
                isForceRefreshCooldown = true,
            ),
            onBackClick = {},
            onAppearanceSelected = {},
            onThemeFamilySelected = {},
            onCheckInsEnabledChange = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenHealthConnectBothPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        SettingsScreen(
            uiState = SettingsUiState(
                appearance = AppearanceUi.Dark,
                themeFamily = ThemeFamilyUi.Stratum,
                healthConnectEnabled = true,
                healthConnectWriteGranted = true,
                healthConnectReadsGranted = true,
            ),
            onBackClick = {},
            onAppearanceSelected = {},
            onThemeFamilySelected = {},
            onCheckInsEnabledChange = {},
        )
    }
}
