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
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ImportDataScreen(
            onBackClick = {},
            onImportLogsClick = {},
            onImportProgramClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ExportDataScreenPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ExportDataScreen(
            onBackClick = {},
            onExportLogsClick = {},
            onExportProgramsClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ExportLogsScreenPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ExportLogsScreen(
            uiState =
                ExportLogsUiState(
                    rangePreset = ExportRangePresetUi.Last30,
                    preview = ExportLogPreviewUi(sessionCount = 12, setCount = 148),
                ),
            onBackClick = {},
            onRangePresetSelected = {},
            onCustomDatesChanged = { _, _ -> },
            onExportClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ExportLogsScreenCustomPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ExportLogsScreen(
            uiState =
                ExportLogsUiState(
                    rangePreset = ExportRangePresetUi.Custom,
                    customStart = LocalDate.of(2026, 1, 1),
                    customEnd = LocalDate.of(2026, 7, 16),
                    preview = ExportLogPreviewUi(sessionCount = 40, setCount = 520),
                ),
            onBackClick = {},
            onRangePresetSelected = {},
            onCustomDatesChanged = { _, _ -> },
            onExportClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ExportProgramsScreenPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
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

@PreviewLightDark
@Composable
private fun ImportLogsScreenIdlePreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ImportLogsScreen(
            uiState = ImportLogsUiState(isAuthenticated = true),
            onBackClick = {},
            onFilesSelected = {},
            onReadFilesFailed = {},
            onCancelImport = {},
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ImportLogsScreenProgressPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ImportLogsScreen(
            uiState =
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
                ),
            onBackClick = {},
            onFilesSelected = {},
            onReadFilesFailed = {},
            onCancelImport = {},
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ImportLogsScreenSuccessPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ImportLogsScreen(
            uiState =
                ImportLogsUiState(
                    isAuthenticated = true,
                    mergeStats =
                        MergeStatsUi(
                            sessionsInserted = 12,
                            sessionsSkipped = 3,
                            setsInserted = 140,
                            setsSkipped = 20,
                        ),
                ),
            onBackClick = {},
            onFilesSelected = {},
            onReadFilesFailed = {},
            onCancelImport = {},
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ImportProgramScreenPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ImportProgramScreen(
            uiState = ImportProgramUiState(isAuthenticated = true),
            onBackClick = {},
            onFileSelected = { _, _ -> },
            onReadFilesFailed = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ImportProgramScreenSuccessPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ImportProgramScreen(
            uiState =
                ImportProgramUiState(
                    isAuthenticated = true,
                    phase = ImportProgramPhaseUi.Complete,
                    importedCount = 2,
                    skippedCount = 0,
                    maxTimelineIndex = 4,
                ),
            onBackClick = {},
            onFileSelected = { _, _ -> },
            onReadFilesFailed = {},
        )
    }
}
