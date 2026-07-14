package com.stratum.core.ui.component

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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor

private const val REORDER_DRAG_THRESHOLD_DP = 48f

/** One row in [StratumReorderListScreen]. */
data class StratumReorderItem(
    val id: String,
    val name: String,
)

/**
 * Full-screen drag-to-reorder list with Cancel / Confirm chrome.
 * Used for reordering lifts and program workouts (cycle days).
 */
@Composable
fun StratumReorderListScreen(
    title: String,
    items: List<StratumReorderItem>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = items.isNotEmpty(),
    snackbarHostState: SnackbarHostState? = null,
) {
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title = title,
                navigationIcon = {
                    StratumTextButton(
                        text = stringResource(CoreUiR.string.reorder_list_cancel),
                        onClick = onCancel,
                        contentColor = StratumThemeAccessor.colors.error,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                actions = {
                    StratumTextButton(
                        text = stringResource(CoreUiR.string.reorder_list_confirm),
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        contentColor = StratumThemeAccessor.colors.accent,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                StratumText(text = emptyMessage, muted = true)
            }
        } else {
            StratumReorderList(
                items = items,
                onReorder = onReorder,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            )
        }
    }
}

@Composable
private fun StratumReorderList(
    items: List<StratumReorderItem>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current.density
    val dragA11y = stringResource(CoreUiR.string.reorder_list_drag_a11y)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val isDragging = index == draggedIndex
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color =
                                if (isDragging) {
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
                    text = item.name,
                    style = StratumThemeAccessor.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_drag_handle),
                    contentDescription = dragA11y,
                    tint = StratumThemeAccessor.colors.textMuted,
                    modifier =
                        Modifier
                            .size(24.dp)
                            .pointerInput(items.size) {
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
                                        while (dragOffset > thresholdPx && draggedIndex < items.lastIndex) {
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

@Preview(name = "Reorder list light", showBackground = true)
@Composable
internal fun StratumReorderListScreenPreviewLight() {
    StratumTheme(darkTheme = false) {
        StratumReorderListScreenPreviewContent()
    }
}

@Preview(name = "Reorder list dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun StratumReorderListScreenPreviewDark() {
    StratumTheme(darkTheme = true) {
        StratumReorderListScreenPreviewContent()
    }
}

@Composable
private fun StratumReorderListScreenPreviewContent() {
    var items by remember {
        mutableStateOf(
            listOf(
                StratumReorderItem("1", "Squat"),
                StratumReorderItem("2", "Bench Press"),
                StratumReorderItem("3", "Row"),
            ),
        )
    }
    StratumReorderListScreen(
        title = "Reorder Lifts",
        items = items,
        onReorder = { from, to ->
            if (from !in items.indices || to !in items.indices) return@StratumReorderListScreen
            val mutable = items.toMutableList()
            val moved = mutable.removeAt(from)
            mutable.add(to, moved)
            items = mutable
        },
        onCancel = {},
        onConfirm = {},
        emptyMessage = "Nothing to reorder.",
    )
}
