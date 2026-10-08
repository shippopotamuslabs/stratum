package com.stratum.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.settings.ExportLogPreviewUi
import com.stratum.feature.settings.ExportLogsUiState
import com.stratum.feature.settings.ExportProgramRowUi
import com.stratum.feature.settings.ExportProgramsUiState
import com.stratum.feature.settings.ExportRangePresetUi
import com.stratum.feature.settings.ImportLogsPhaseUi
import com.stratum.feature.settings.ImportLogsUiState
import com.stratum.feature.settings.ImportProgressUi
import com.stratum.feature.settings.ImportProgramPhaseUi
import com.stratum.feature.settings.ImportProgramUiState
import com.stratum.feature.settings.MergeStatsUi
import java.time.LocalDate

@PreviewLightDark
@Composable
private fun ImportDataScreenPreview() {
    ImportDataPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ExportDataScreenPreview() {
    ExportDataPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ExportLogsScreenPreview() {
    ExportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewExportLogsLast30(),
    )
}

@PreviewLightDark
@Composable
private fun ExportLogsScreenCustomPreview() {
    ExportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewExportLogsCustom(),
    )
}

@PreviewLightDark
@Composable
private fun ExportProgramsScreenPreview() {
    ExportProgramsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ImportLogsScreenIdlePreview() {
    ImportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportLogsIdle(),
    )
}

@PreviewLightDark
@Composable
private fun ImportLogsScreenProgressPreview() {
    ImportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportLogsProgress(),
    )
}

@PreviewLightDark
@Composable
private fun ImportLogsScreenSuccessPreview() {
    ImportLogsPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportLogsSuccess(),
    )
}

@PreviewLightDark
@Composable
private fun ImportProgramScreenPreview() {
    ImportProgramPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportProgramIdle(),
    )
}

@PreviewLightDark
@Composable
private fun ImportProgramScreenSuccessPreview() {
    ImportProgramPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewImportProgramSuccess(),
    )
}

@Composable
internal fun ImportDataPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ImportDataScreen(
            onBackClick = {},
            onImportLogsClick = {},
            onImportProgramClick = {},
        )
    }
}

@Composable
internal fun ExportDataPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ExportDataScreen(
            onBackClick = {},
            onExportLogsClick = {},
            onExportProgramsClick = {},
        )
    }
}

@Composable
internal fun ExportLogsPreviewContent(
    family: StratumThemeId,
    uiState: ExportLogsUiState,
) {
    StratumTheme(themeId = family) {
        ExportLogsScreen(
            uiState = uiState,
            onBackClick = {},
            onRangePresetSelected = {},
            onCustomDatesChanged = { _, _ -> },
            onExportClick = {},
        )
    }
}

@Composable
internal fun ExportProgramsPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ExportProgramsScreen(
            uiState =
                ExportProgramsUiState(
                    programs =
                        listOf(
                            ExportProgramRowUi("1", "Strength Block A", isActive = true),
                            ExportProgramRowUi("2", "Hypertrophy Bridge", isActive = false),
                            ExportProgramRowUi("3", "Deload Week", isActive = false),
                        ),
                    selectedIds = setOf("1", "2"),
                ),
            onBackClick = {},
            onToggleProgram = {},
            onSelectAll = {},
            onClearSelection = {},
            onExportClick = {},
        )
    }
}

@Composable
internal fun ImportLogsPreviewContent(
    family: StratumThemeId,
    uiState: ImportLogsUiState,
) {
    StratumTheme(themeId = family) {
        ImportLogsScreen(
            uiState = uiState,
            onBackClick = {},
            onBeginImportClick = {},
            onWeightUnitConfirmed = {},
            onWeightUnitPromptDismissed = {},
            onFilePickerLaunched = {},
            onFilesSelected = {},
            onReadFilesFailed = {},
            onCancelImport = {},
            onRetry = {},
        )
    }
}

@Composable
internal fun ImportProgramPreviewContent(
    family: StratumThemeId,
    uiState: ImportProgramUiState,
) {
    StratumTheme(themeId = family) {
        ImportProgramScreen(
            uiState = uiState,
            onBackClick = {},
            onFileSelected = { _, _ -> },
            onReadFilesFailed = {},
        )
    }
}

internal fun previewExportLogsLast30(): ExportLogsUiState =
    ExportLogsUiState(
        rangePreset = ExportRangePresetUi.Last30,
        preview = ExportLogPreviewUi(sessionCount = 12, setCount = 148),
    )

internal fun previewExportLogsCustom(): ExportLogsUiState =
    ExportLogsUiState(
        rangePreset = ExportRangePresetUi.Custom,
        customStart = LocalDate.of(2026, 1, 1),
        customEnd = LocalDate.of(2026, 7, 16),
        preview = ExportLogPreviewUi(sessionCount = 40, setCount = 520),
    )

internal fun previewImportLogsIdle(): ImportLogsUiState =
    ImportLogsUiState(isAuthenticated = true)

internal fun previewImportLogsProgress(): ImportLogsUiState =
    ImportLogsUiState(
        isAuthenticated = true,
        isImporting = true,
        maxTimelineIndex = 1,
        progress =
            ImportProgressUi(
                phase = ImportLogsPhaseUi.ConvertingFull,
                fileIndex = 1,
                fileCount = 2,
            ),
    )

internal fun previewImportLogsSuccess(): ImportLogsUiState =
    ImportLogsUiState(
        isAuthenticated = true,
        mergeStats =
            MergeStatsUi(
                sessionsInserted = 12,
                sessionsSkipped = 3,
                setsInserted = 140,
                setsSkipped = 20,
            ),
    )

internal fun previewImportProgramIdle(): ImportProgramUiState =
    ImportProgramUiState(isAuthenticated = true)

internal fun previewImportProgramSuccess(): ImportProgramUiState =
    ImportProgramUiState(
        isAuthenticated = true,
        phase = ImportProgramPhaseUi.Complete,
        importedCount = 2,
        skippedCount = 0,
        maxTimelineIndex = 4,
    )
