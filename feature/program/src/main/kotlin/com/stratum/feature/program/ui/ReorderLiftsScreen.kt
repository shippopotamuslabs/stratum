package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumReorderItem
import com.stratum.core.ui.component.StratumReorderListScreen

@Composable
fun ReorderLiftsRoute(
    workoutId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReorderLiftsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    val lifts by viewModel.lifts.collectAsState()

    ReorderLiftsScreen(
        lifts = lifts,
        onReorder = viewModel::reorderLifts,
        onCancel = onBackClick,
        onConfirm = {
            viewModel.confirmReorder(workoutId)
            onBackClick()
        },
        modifier = modifier,
    )
}

@Composable
fun ReorderLiftsScreen(
    lifts: List<ReorderLiftUiState>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumReorderListScreen(
        title = stringResource(CoreUiR.string.reorder_lifts_title),
        items = lifts.map { lift -> StratumReorderItem(id = lift.workoutLiftId, name = lift.name) },
        onReorder = onReorder,
        onCancel = onCancel,
        onConfirm = onConfirm,
        emptyMessage = stringResource(CoreUiR.string.reorder_lifts_empty),
        modifier = modifier,
    )
}
