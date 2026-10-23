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
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.importing.ImportMimeTypes
import com.stratum.core.ui.importing.StratumImportInfoCard
import com.stratum.core.ui.importing.StratumImportProgressSection
import com.stratum.core.ui.importing.StratumImportResultCard
import com.stratum.core.ui.importing.StratumImportResultTone
import com.stratum.core.ui.importing.StratumImportStatusBanner
import com.stratum.core.ui.importing.loadImportFile
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.settings.ImportProgramPhaseUi
import com.stratum.feature.settings.ImportProgramUiState
import com.stratum.feature.settings.R
import com.stratum.feature.settings.programImportTimelineIndex
import kotlinx.coroutines.launch

@Composable
fun ImportProgramScreen(
    uiState: ImportProgramUiState,
    onBackClick: () -> Unit,
    onFileSelected: (ByteArray, String) -> Unit,
    onReadFilesFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                loadImportFile(context, uri)
                    .onSuccess { file -> onFileSelected(file.bytes, file.fileName) }
                    .onFailure { error ->
                        onReadFilesFailed(
                            error.message?.takeIf { it.isNotBlank() }
                                ?: "Could not read the selected file.",
                        )
                    }
            }
        }

    val isBusy = uiState.isImporting
    val isComplete = isProgramImportComplete(uiState)
    val showProgressTimeline = shouldShowProgramImportTimeline(uiState, isBusy, isComplete)
    val timelineLabels =
        listOf(
            stringResource(CoreUiR.string.import_program_timeline_upload),
            stringResource(CoreUiR.string.import_program_timeline_convert),
            stringResource(CoreUiR.string.import_program_timeline_match),
            stringResource(CoreUiR.string.import_program_timeline_done),
        )
    val activeTimelineIndex =
        maxOf(
            uiState.maxTimelineIndex,
            uiState.phase.programImportTimelineIndex(),
        )

    // Glass (§12.8, STR-161): under the route's glass host the top bar overlays
    // and the content column carries the top clearance inside its scroll, so
    // content scrolls beneath the glass. Solid hosts keep the in-flow geometry.
    StratumDetailScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.import_program_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.settings_navigate_back),
                    )
                },
            )
        },
    ) { chrome ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = StratumSpacing.s16)
                    .padding(top = StratumSpacing.s16 + chrome.top, bottom = StratumSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        ) {
            StratumText(
                text = stringResource(R.string.import_program_description),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )

            StratumImportInfoCard(
                title = stringResource(R.string.import_program_formats_title),
                body = stringResource(R.string.import_program_formats_body),
                note = stringResource(R.string.import_program_library_note),
            )

            if (uiState.signedOutGuidanceVisible || !uiState.isAuthenticated) {
                StratumImportStatusBanner(
                    title = stringResource(R.string.import_signed_out_title),
                    body = stringResource(R.string.import_signed_out_guidance),
                )
            }

            if (showProgressTimeline) {
                StratumImportProgressSection(
                    title = stringResource(R.string.import_program_progress_title),
                    stepLabels = timelineLabels,
                    activeIndex = activeTimelineIndex,
                )
            }

            if (isComplete) {
                StratumImportResultCard(
                    title = stringResource(R.string.import_program_success_title),
                    body =
                        stringResource(
                            R.string.import_program_success_body,
                            uiState.importedCount,
                            uiState.skippedCount,
                        ),
                    tone = StratumImportResultTone.Success,
                )
            }

            uiState.errorMessage?.let { message ->
                StratumImportResultCard(
                    title = stringResource(R.string.import_program_error_title),
                    body = message,
                    tone = StratumImportResultTone.Error,
                )
            }

            StratumButton(
                text =
                    if (isComplete) {
                        stringResource(R.string.import_program_pick_another)
                    } else {
                        stringResource(R.string.import_program_pick_file)
                    },
                onClick = { picker.launch(ImportMimeTypes.PROGRAM) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && uiState.isAuthenticated,
            )
        }
    }
}

private fun isProgramImportComplete(uiState: ImportProgramUiState): Boolean =
    uiState.phase == ImportProgramPhaseUi.Complete

private fun shouldShowProgramImportTimeline(
    uiState: ImportProgramUiState,
    isBusy: Boolean,
    isComplete: Boolean,
): Boolean {
    if (isBusy) return true
    if (uiState.phase == null) return false
    if (isComplete) return false
    return uiState.errorMessage == null
}
