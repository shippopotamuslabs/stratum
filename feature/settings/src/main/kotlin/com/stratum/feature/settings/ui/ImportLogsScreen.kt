package com.stratum.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.importing.ImportMimeTypes
import com.stratum.core.ui.importing.StratumImportInfoCard
import com.stratum.core.ui.importing.StratumImportProgressSection
import com.stratum.core.ui.importing.StratumImportResultCard
import com.stratum.core.ui.importing.StratumImportResultTone
import com.stratum.core.ui.importing.StratumImportStatusBanner
import com.stratum.core.ui.importing.loadImportFiles
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.settings.ImportLogsUiState
import com.stratum.feature.settings.R
import com.stratum.feature.settings.historicalImportTimelineIndex
import kotlinx.coroutines.launch

@Composable
fun ImportLogsScreen(
    uiState: ImportLogsUiState,
    onBackClick: () -> Unit,
    onFilesSelected: (List<Pair<ByteArray, String>>) -> Unit,
    onReadFilesFailed: (String) -> Unit,
    onCancelImport: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris: List<Uri> ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                loadImportFiles(context, uris)
                    .onSuccess { files ->
                        onFilesSelected(files.map { it.bytes to it.fileName })
                    }
                    .onFailure { error ->
                        onReadFilesFailed(
                            error.message?.takeIf { it.isNotBlank() }
                                ?: "Could not read the selected files.",
                        )
                    }
            }
        }

    val isBusy = uiState.isImporting || uiState.progress != null
    val timelineLabels =
        listOf(
            stringResource(CoreUiR.string.import_timeline_upload),
            stringResource(CoreUiR.string.import_timeline_convert),
            stringResource(CoreUiR.string.import_timeline_catalog),
            stringResource(CoreUiR.string.import_timeline_analyze),
        )
    val activeTimelineIndex =
        maxOf(
            uiState.maxTimelineIndex,
            uiState.progress?.phase.historicalImportTimelineIndex(),
        )
    val fileProgressLabel =
        uiState.progress?.let { progress ->
            val index = progress.fileIndex
            val count = progress.fileCount
            if (index != null && count != null) {
                stringResource(R.string.import_logs_file_progress, index, count)
            } else {
                null
            }
        }

    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.import_logs_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.settings_navigate_back),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = stringResource(R.string.import_logs_description),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )

            StratumImportInfoCard(
                title = stringResource(R.string.import_logs_formats_title),
                body = stringResource(R.string.import_logs_formats_body),
                note = stringResource(R.string.import_logs_merge_safe_note),
            )

            if (uiState.signedOutGuidanceVisible || !uiState.isAuthenticated) {
                StratumImportStatusBanner(
                    title = stringResource(R.string.import_signed_out_title),
                    body = stringResource(R.string.import_signed_out_guidance),
                )
            }

            if (isBusy || (uiState.maxTimelineIndex > 0 && uiState.mergeStats == null)) {
                StratumImportProgressSection(
                    title = stringResource(R.string.import_logs_progress_title),
                    stepLabels = timelineLabels,
                    activeIndex = activeTimelineIndex,
                    fileProgressLabel = fileProgressLabel,
                )
            }

            uiState.mergeStats?.let { stats ->
                StratumImportResultCard(
                    title = stringResource(R.string.import_logs_success_title),
                    body =
                        stringResource(
                            R.string.import_logs_merge_stats,
                            stats.sessionsInserted,
                            stats.sessionsSkipped,
                        ),
                    tone = StratumImportResultTone.Success,
                    detail =
                        stringResource(
                            R.string.import_logs_merge_sets_stats,
                            stats.setsInserted,
                            stats.setsSkipped,
                        ),
                )
            }

            uiState.errorMessage?.let { message ->
                StratumImportResultCard(
                    title = stringResource(R.string.import_logs_error_title),
                    body = message,
                    tone = StratumImportResultTone.Error,
                )
            }

            StratumButton(
                text = stringResource(R.string.import_logs_pick_files),
                onClick = { picker.launch(ImportMimeTypes.HISTORICAL) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && uiState.isAuthenticated,
            )

            if (isBusy) {
                StratumOutlinedButton(
                    text = stringResource(R.string.import_logs_cancel),
                    onClick = onCancelImport,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (uiState.errorMessage != null && !isBusy) {
                StratumOutlinedButton(
                    text = stringResource(R.string.import_logs_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.isAuthenticated,
                )
            }
        }
    }
}
