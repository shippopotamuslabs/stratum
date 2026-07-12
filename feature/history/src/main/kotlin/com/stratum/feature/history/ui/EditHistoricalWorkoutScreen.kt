package com.stratum.feature.history.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.StratumTopBarTextButton
import com.stratum.core.ui.component.workoutlog.LocalSetLogKeyboardHost
import com.stratum.core.ui.component.workoutlog.ProvideSetLogKeyboardHost
import com.stratum.core.ui.component.workoutlog.StratumWorkoutLogLiftPanel
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetRowActions
import com.stratum.core.ui.component.workoutlog.rememberOverlayDockListBinding
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.R

@Composable
fun EditHistoricalWorkoutScreen(
    state: EditHistoricalWorkoutUiState,
    onBackClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onWeightChange: (liftKey: String, setId: String, text: String) -> Unit,
    onRepsChange: (liftKey: String, setId: String, text: String) -> Unit,
    onRirChange: (liftKey: String, setId: String, rir: Int) -> Unit,
    onAddSet: (liftKey: String) -> Unit,
    onDeleteSet: (liftKey: String, setId: String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val loaded = state as? EditHistoricalWorkoutUiState.Loaded
    var showDiscardDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = loaded?.isDirty == true) {
        showDiscardDialog = true
    }

    StratumScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title =
                    when (state) {
                        is EditHistoricalWorkoutUiState.Loaded -> state.workoutName
                        else -> stringResource(R.string.history_edit_title)
                    },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = {
                            if (loaded?.isDirty == true) {
                                showDiscardDialog = true
                            } else {
                                onBackClick()
                            }
                        },
                        contentDescription = stringResource(R.string.history_navigate_back),
                    )
                },
                actions = {
                    if (loaded != null && loaded.isDirty) {
                        StratumTopBarTextButton(
                            text =
                                if (loaded.isSaving) {
                                    stringResource(R.string.history_edit_saving)
                                } else {
                                    stringResource(R.string.history_edit_confirm)
                                },
                            onClick = onConfirmClick,
                            enabled = !loaded.isSaving,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            EditHistoricalWorkoutUiState.Loading ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(text = "…", muted = true)
                }
            EditHistoricalWorkoutUiState.Error ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(
                        text = stringResource(R.string.history_edit_load_error),
                        muted = true,
                    )
                }
            is EditHistoricalWorkoutUiState.Loaded -> {
                val addSetCd = stringResource(R.string.history_edit_add_set)
                val deleteSetCd = stringResource(R.string.history_edit_delete_set)
                val listState = rememberLazyListState()
                ProvideSetLogKeyboardHost(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(vertical = 12.dp),
                ) { listModifier ->
                    val keyboardHost = LocalSetLogKeyboardHost.current
                    val dockHeightDp =
                        rememberOverlayDockListBinding(
                            listState = listState,
                            dock = keyboardHost?.overlay,
                        )

                    LazyColumn(
                        state = listState,
                        modifier = listModifier,
                        contentPadding = PaddingValues(bottom = dockHeightDp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.lifts, key = { it.key }) { lift ->
                            StratumWorkoutLogLiftPanel(
                                lift = lift,
                                onAddSet = { onAddSet(lift.key) },
                                addSetContentDescription = addSetCd,
                                setRowActions = { set ->
                                    WorkoutLogSetRowActions(
                                        onWeightChange = { text ->
                                            onWeightChange(lift.key, set.setKey, text)
                                        },
                                        onRepsChange = { text ->
                                            onRepsChange(lift.key, set.setKey, text)
                                        },
                                        onRirChange = { rir ->
                                            onRirChange(lift.key, set.setKey, rir)
                                        },
                                        onDelete = { onDeleteSet(lift.key, set.setKey) },
                                        deleteContentDescription = deleteSetCd,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        DiscardHistoricalEditSheet(
            onConfirmDiscard = {
                showDiscardDialog = false
                onBackClick()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
}

@Composable
private fun DiscardHistoricalEditSheet(
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.history_edit_discard_title),
    ) {
        StratumText(
            text = stringResource(R.string.history_edit_discard_message),
            style = StratumThemeAccessor.typography.bodyLarge,
        )
        StratumButton(
            text = stringResource(R.string.history_edit_discard_confirm),
            onClick = onConfirmDiscard,
            modifier = Modifier.fillMaxWidth(),
            destructive = true,
        )
        StratumOutlinedButton(
            text = stringResource(R.string.history_edit_discard_keep),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
