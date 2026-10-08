package com.stratum.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for settings (STR-95). Thin `@PreviewTest` wrappers over the shared preview
 * content.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun settingsDefaultTonal() {
    SettingsScreenPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewSettingsDefault(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun settingsDefaultNeo() {
    SettingsScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewSettingsDefault(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun settingsDebugTonal() {
    SettingsScreenPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewSettingsDebug(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun settingsDebugNeo() {
    SettingsScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewSettingsDebug(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun settingsHealthBothTonal() {
    SettingsScreenPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewSettingsHealthBoth(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun settingsHealthBothNeo() {
    SettingsScreenPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewSettingsHealthBoth(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun legalTonal() {
    LegalScreenPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun legalNeo() {
    LegalScreenPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun importDataTonal() {
    ImportDataPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun importDataNeo() {
    ImportDataPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportDataTonal() {
    ExportDataPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportDataNeo() {
    ExportDataPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportLogsLast30Tonal() {
    ExportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewExportLogsLast30(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportLogsLast30Neo() {
    ExportLogsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewExportLogsLast30(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportLogsCustomTonal() {
    ExportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewExportLogsCustom(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportLogsCustomNeo() {
    ExportLogsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewExportLogsCustom(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportProgramsTonal() {
    ExportProgramsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewExportProgramsSelection(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun exportProgramsNeo() {
    ExportProgramsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewExportProgramsSelection(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importLogsIdleTonal() {
    ImportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportLogsIdle(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importLogsIdleNeo() {
    ImportLogsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewImportLogsIdle(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importLogsProgressTonal() {
    ImportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportLogsProgress(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importLogsProgressNeo() {
    ImportLogsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewImportLogsProgress(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importLogsSuccessTonal() {
    ImportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportLogsSuccess(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importLogsSuccessNeo() {
    ImportLogsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewImportLogsSuccess(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importProgramIdleTonal() {
    ImportProgramPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportProgramIdle(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importProgramIdleNeo() {
    ImportProgramPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewImportProgramIdle(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importProgramSuccessTonal() {
    ImportProgramPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportProgramSuccess(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun importProgramSuccessNeo() {
    ImportProgramPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        uiState = previewImportProgramSuccess(),
    )
}
