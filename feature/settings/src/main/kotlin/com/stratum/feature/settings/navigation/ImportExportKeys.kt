package com.stratum.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.settings.ExportLogsViewModel
import com.stratum.feature.settings.ExportProgramsViewModel
import com.stratum.feature.settings.ImportLogsViewModel
import com.stratum.feature.settings.ImportProgramViewModel
import com.stratum.feature.settings.shareJsonExport
import com.stratum.feature.settings.ui.ExportDataScreen
import com.stratum.feature.settings.ui.ExportLogsScreen
import com.stratum.feature.settings.ui.ExportProgramsScreen
import com.stratum.feature.settings.ui.ImportDataScreen
import com.stratum.feature.settings.ui.ImportLogsScreen
import com.stratum.feature.settings.ui.ImportProgramScreen
import kotlinx.serialization.Serializable

@Serializable data object ImportDataKey : NavKey

@Serializable data object ImportLogsKey : NavKey

@Serializable data object ImportProgramKey : NavKey

@Serializable data object ExportDataKey : NavKey

@Serializable data object ExportLogsKey : NavKey

@Serializable data object ExportProgramsKey : NavKey

fun EntryProviderScope<NavKey>.importDataEntry(
    onBackClick: () -> Unit,
    onNavigateToImportLogs: () -> Unit,
    onNavigateToImportProgram: () -> Unit,
) {
    entry<ImportDataKey> {
        SettingsGlassRoute {
            ImportDataScreen(
                onBackClick = onBackClick,
                onImportLogsClick = onNavigateToImportLogs,
                onImportProgramClick = onNavigateToImportProgram,
            )
        }
    }
}

fun EntryProviderScope<NavKey>.importLogsEntry(onBackClick: () -> Unit) {
    entry<ImportLogsKey> {
        val viewModel: ImportLogsViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsGlassRoute {
            ImportLogsScreen(
                uiState = uiState,
                onBackClick = onBackClick,
                onBeginImportClick = viewModel::onBeginImportClick,
                onWeightUnitConfirmed = viewModel::onWeightUnitConfirmed,
                onWeightUnitPromptDismissed = viewModel::onWeightUnitPromptDismissed,
                onFilePickerLaunched = viewModel::onFilePickerLaunched,
                onFilesSelected = viewModel::onFilesSelected,
                onReadFilesFailed = viewModel::onReadFilesFailed,
                onCancelImport = viewModel::onCancelImport,
                onRetry = viewModel::onRetryFromFailedFile,
            )
        }
    }
}

fun EntryProviderScope<NavKey>.importProgramEntry(onBackClick: () -> Unit) {
    entry<ImportProgramKey> {
        val viewModel: ImportProgramViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsGlassRoute {
            ImportProgramScreen(
                uiState = uiState,
                onBackClick = onBackClick,
                onFileSelected = viewModel::onFileSelected,
                onReadFilesFailed = viewModel::onReadFilesFailed,
            )
        }
    }
}

fun EntryProviderScope<NavKey>.exportDataEntry(
    onBackClick: () -> Unit,
    onNavigateToExportLogs: () -> Unit,
    onNavigateToExportPrograms: () -> Unit,
) {
    entry<ExportDataKey> {
        SettingsGlassRoute {
            ExportDataScreen(
                onBackClick = onBackClick,
                onExportLogsClick = onNavigateToExportLogs,
                onExportProgramsClick = onNavigateToExportPrograms,
            )
        }
    }
}

fun EntryProviderScope<NavKey>.exportLogsEntry(onBackClick: () -> Unit) {
    entry<ExportLogsKey> {
        val viewModel: ExportLogsViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.shareEvents.collect { file ->
                shareJsonExport(context, file)
            }
        }
        SettingsGlassRoute {
            ExportLogsScreen(
                uiState = uiState,
                onBackClick = onBackClick,
                onRangePresetSelected = viewModel::onRangePresetSelected,
                onCustomDatesChanged = viewModel::onCustomDatesChanged,
                onExportClick = viewModel::onExportClick,
            )
        }
    }
}

fun EntryProviderScope<NavKey>.exportProgramsEntry(onBackClick: () -> Unit) {
    entry<ExportProgramsKey> {
        val viewModel: ExportProgramsViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.shareEvents.collect { file ->
                shareJsonExport(context, file)
            }
        }
        SettingsGlassRoute {
            ExportProgramsScreen(
                uiState = uiState,
                onBackClick = onBackClick,
                onToggleProgram = viewModel::onToggleProgram,
                onSelectAll = viewModel::onSelectAll,
                onClearSelection = viewModel::onClearSelection,
                onExportClick = viewModel::onExportClick,
            )
        }
    }
}
