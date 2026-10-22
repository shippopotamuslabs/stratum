package com.stratum.feature.program.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumProgramLibraryCard
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumSwipeToRevealRow
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember

@Composable
fun ProgramLibraryScreen(
    state: ProgramLibraryUiState,
    onBackClick: () -> Unit,
    onProgramClick: (programId: String) -> Unit,
    onWorkoutClick: (programId: String, workoutId: String) -> Unit,
    onActivateClick: (programId: String) -> Unit,
    onExpandToggle: (programId: String) -> Unit,
    onDeleteRequest: (programId: String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onActivateConfirm: () -> Unit,
    onActivateDismiss: () -> Unit,
    onUserMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(
        userMessage = state.userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = onUserMessageShown,
    )
    // Glass (§12.8, STR-158): under the route's glass host the top bar overlays
    // and the list carries the top clearance as content padding, so cards
    // scroll beneath the glass. Solid hosts keep the in-flow geometry exactly
    // (chrome.top is 0 there).
    StratumDetailScaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.program_library_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.program_library_back),
                    )
                },
            )
        },
    ) { chrome ->
        if (state.programs.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(StratumSpacing.s24),
                contentAlignment = Alignment.Center,
            ) {
                StratumText(
                    text = stringResource(R.string.program_library_empty),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = StratumSpacing.s12,
                        end = StratumSpacing.s12,
                        top = StratumSpacing.s12 + chrome.top,
                        bottom = StratumSpacing.s12,
                    ),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
            ) {
                items(
                    items = state.programs,
                    key = { program -> program.programId },
                ) { program ->
                    val expanded = program.programId in state.expandedProgramIds
                    StratumSwipeToRevealRow(
                        onActionClick = { onDeleteRequest(program.programId) },
                        actionContentDescription =
                            stringResource(
                                R.string.program_library_delete_content_description,
                                program.name,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        StratumProgramLibraryCard(
                            state = program,
                            expanded = expanded,
                            activeLabel = stringResource(R.string.program_library_active_badge),
                            workoutCountLabel =
                                pluralStringResource(
                                    R.plurals.program_library_workout_count,
                                    program.workoutCount,
                                    program.workoutCount,
                                ),
                            onExpandedChange = { onExpandToggle(program.programId) },
                            onProgramClick = { onProgramClick(program.programId) },
                            onActivateClick = { onActivateClick(program.programId) },
                            onWorkoutClick = { workoutId ->
                                onWorkoutClick(program.programId, workoutId)
                            },
                            expandContentDescription =
                                stringResource(
                                    R.string.program_library_expand_a11y,
                                    program.name,
                                ),
                            collapseContentDescription =
                                stringResource(
                                    R.string.program_library_collapse_a11y,
                                    program.name,
                                ),
                        )
                    }
                }
            }
        }
    }

    val pendingId = state.pendingDeleteProgramId
    if (pendingId != null) {
        if (state.pendingDeleteHasActiveSession) {
            // Block delete while an in-progress session is linked — avoid ghost active rows.
            StratumConfirmDialog(
                title = stringResource(R.string.program_library_delete_title),
                body = stringResource(R.string.program_library_delete_active_session_body),
                confirmLabel = stringResource(R.string.program_library_delete_blocked_confirm),
                dismissLabel = stringResource(R.string.program_library_delete_cancel),
                onConfirm = onDeleteDismiss,
                onDismiss = onDeleteDismiss,
                destructiveConfirm = false,
                confirmEnabled = true,
            )
        } else {
            StratumConfirmDialog(
                title = stringResource(R.string.program_library_delete_title),
                body =
                    stringResource(
                        R.string.program_library_delete_body,
                        state.pendingDeleteProgramName.orEmpty(),
                    ),
                confirmLabel = stringResource(R.string.program_library_delete_confirm),
                dismissLabel = stringResource(R.string.program_library_delete_cancel),
                onConfirm = onDeleteConfirm,
                onDismiss = onDeleteDismiss,
                destructiveConfirm = true,
                confirmEnabled = !state.isDeleting,
            )
        }
    }

    state.pendingActivateProgramId?.let {
        StratumConfirmDialog(
            title = stringResource(R.string.program_library_activate_discard_title),
            body =
                stringResource(
                    R.string.program_library_activate_discard_body,
                    state.pendingActivateProgramName.orEmpty(),
                ),
            confirmLabel = stringResource(R.string.program_library_activate_discard_confirm),
            dismissLabel = stringResource(R.string.program_library_activate_discard_cancel),
            onConfirm = onActivateConfirm,
            onDismiss = onActivateDismiss,
            destructiveConfirm = true,
            confirmEnabled = true,
        )
    }
}
