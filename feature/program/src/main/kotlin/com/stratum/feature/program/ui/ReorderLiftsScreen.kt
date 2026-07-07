package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor

private const val REORDER_DRAG_THRESHOLD_DP = 48f

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
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                title = stringResource(CoreUiR.string.reorder_lifts_title),
                navigationIcon = {
                    StratumTextButton(
                        text = stringResource(CoreUiR.string.reorder_lifts_cancel),
                        onClick = onCancel,
                        contentColor = StratumThemeAccessor.colors.error,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                actions = {
                    StratumTextButton(
                        text = stringResource(CoreUiR.string.reorder_lifts_confirm),
                        onClick = onConfirm,
                        contentColor = StratumThemeAccessor.colors.accent,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
            )
        }
    ) { padding ->
        if (lifts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                StratumText(text = "No lifts to reorder.", muted = true)
            }
        } else {
            ReorderLiftsList(
                lifts = lifts,
                onReorder = onReorder,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun ReorderLiftsList(
    lifts: List<ReorderLiftUiState>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current.density

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(lifts, key = { _, lift -> lift.workoutLiftId }) { index, lift ->
            val isDragging = index == draggedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isDragging) {
                            StratumThemeAccessor.colors.surfaceInset
                        } else {
                            StratumThemeAccessor.colors.surfaceRaised
                        },
                        shape = StratumThemeAccessor.shapes.panel,
                    )
                    .border(
                        width = StratumThemeAccessor.shapes.borderWidth,
                        color = StratumThemeAccessor.colors.borderSubtle,
                        shape = StratumThemeAccessor.shapes.panel,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StratumText(
                    text = lift.name,
                    style = StratumThemeAccessor.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_drag_handle),
                    contentDescription = "Drag to reorder",
                    tint = StratumThemeAccessor.colors.textMuted,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(lifts.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggedIndex = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggedIndex = -1
                                    dragOffset = 0f
                                },
                                onDrag = { _, dragAmount ->
                                    if (draggedIndex < 0) return@detectDragGestures
                                    dragOffset += dragAmount.y
                                    val thresholdPx = REORDER_DRAG_THRESHOLD_DP * density
                                    while (dragOffset > thresholdPx && draggedIndex < lifts.lastIndex) {
                                        onReorder(draggedIndex, draggedIndex + 1)
                                        draggedIndex += 1
                                        dragOffset -= thresholdPx
                                    }
                                    while (dragOffset < -thresholdPx && draggedIndex > 0) {
                                        onReorder(draggedIndex, draggedIndex - 1)
                                        draggedIndex -= 1
                                        dragOffset += thresholdPx
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}
