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
    SettingsScreenPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewSettingsDefault(),
    )
}

@PreviewLightDark
@Composable
private fun SettingsScreenNeoPreview() {
    SettingsScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewSettingsDebug(),
    )
}

@PreviewLightDark
@Composable
private fun SettingsScreenHealthConnectBothPreview() {
    SettingsScreenPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewSettingsHealthBoth(),
    )
}

@Composable
internal fun SettingsScreenPreviewContent(
    family: StratumThemeId,
    uiState: SettingsUiState,
) {
    StratumTheme(themeId = family) {
        SettingsScreen(
            uiState = uiState,
            onBackClick = {},
            onAppearanceSelected = {},
            onThemeFamilySelected = {},
            onCheckInsEnabledChange = {},
        )
    }
}

internal fun previewSettingsDefault(): SettingsUiState =
    SettingsUiState(
        appearance = AppearanceUi.System,
        themeFamily = ThemeFamilyUi.Stratum,
        checkInsEnabled = true,
        equipmentProposalsEnabled = true,
        repRangeProposalsEnabled = false,
        weightUnit = WeightUnitUi.Lbs,
    )

internal fun previewSettingsDebug(): SettingsUiState =
    SettingsUiState(
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
    )

internal fun previewSettingsHealthBoth(): SettingsUiState =
    SettingsUiState(
        appearance = AppearanceUi.Dark,
        themeFamily = ThemeFamilyUi.Stratum,
        healthConnectEnabled = true,
        healthConnectWriteGranted = true,
        healthConnectReadsGranted = true,
    )
