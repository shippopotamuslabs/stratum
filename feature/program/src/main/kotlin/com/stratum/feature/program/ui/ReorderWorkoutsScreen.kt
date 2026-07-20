package com.stratum.feature.program.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumReorderItem
import com.stratum.core.ui.component.StratumReorderListScreen
import com.stratum.core.ui.component.UserMessageEffect

@Composable
fun ReorderWorkoutsRoute(
    programId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReorderWorkoutsViewModel = hiltViewModel(),
) {
    LaunchedEffect(programId) {
        viewModel.initialize(programId)
    }

    val days by viewModel.days.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )

    ReorderWorkoutsScreen(
        days = days,
        confirmEnabled = isLoaded && days.isNotEmpty(),
        onReorder = viewModel::reorderDays,
        onCancel = onBackClick,
        onConfirm = {
            viewModel.confirmReorder(onDone = onBackClick)
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun ReorderWorkoutsScreen(
    days: List<ReorderWorkoutUiState>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = days.isNotEmpty(),
    snackbarHostState: SnackbarHostState? = null,
) {
    StratumReorderListScreen(
        title = stringResource(CoreUiR.string.reorder_workouts_title),
        items = days.map { day -> StratumReorderItem(id = day.dayId, name = day.name) },
        onReorder = onReorder,
        onCancel = onCancel,
        onConfirm = onConfirm,
        emptyMessage = stringResource(CoreUiR.string.reorder_workouts_empty),
        confirmEnabled = confirmEnabled,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
