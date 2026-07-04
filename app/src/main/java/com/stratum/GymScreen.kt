package com.stratum

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.FilterDropdownRow
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapOptionRow
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumMuscleFilterRow
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumSearchField
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import kotlinx.coroutines.launch

@Composable
fun GymScreen(
    onCreateNewLift: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GymViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GymScreen(
        state = state,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onFilterIntent = viewModel::onFilterIntent,
        onLiftClick = { /* navigate to lift detail in future slice */ },
        onCreateNewLift = onCreateNewLift,
        modifier = modifier,
    )
}

@Composable
fun GymScreen(
    state: LiftSwapSheetState,
    onSearchQueryChanged: (String) -> Unit,
    onFilterIntent: (LiftSwapFilterIntent) -> Unit,
    onLiftClick: (String) -> Unit,
    onCreateNewLift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumTabScaffold(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = {
            StratumTopBar(
                titleContent = {
                    StratumSearchField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        placeholder = stringResource(UiR.string.lift_swap_search_placeholder),
                    )
                },
                actions = {
                    StratumIconButton(
                        onClick = onCreateNewLift,
                        containerColor = Color.Transparent,
                    ) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_add),
                            contentDescription = stringResource(UiR.string.lift_swap_create_new_lift),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                }
            )
        },
    ) {
        FilterDropdownRow(
            state = state,
            onFilterIntent = onFilterIntent,
        )

        StratumMuscleFilterRow(
            upperCards = state.muscleGroups.upper,
            lowerCards = state.muscleGroups.lower,
            selectedMuscles = state.selectedMuscles,
            onMuscleToggled = { muscle -> onFilterIntent(LiftSwapFilterIntent.ToggleMuscle(muscle)) },
        )

        val listState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // end padding reserves space so rows don't slide under the scrollbar
                contentPadding = PaddingValues(bottom = 16.dp, end = 24.dp),
            ) {
                items(state.options, key = { option -> option.id }) { option ->
                    LiftSwapOptionRow(
                        option = option,
                        onClick = { onLiftClick(option.id) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = StratumThemeAccessor.colors.textMuted,
                            )
                        }
                    )
                }

                if (state.options.isEmpty()) {
                    item(key = "empty-state") {
                        StratumText(
                            text = stringResource(UiR.string.lift_swap_empty),
                            muted = true,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }

            // Draggable scrollbar — overlays the Box, outside the column's horizontal padding
            if (state.options.isNotEmpty()) {
                DraggableScrollbar(
                    state = listState,
                    itemsCount = state.options.size,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun DraggableScrollbar(
    state: LazyListState,
    itemsCount: Int,
    modifier: Modifier = Modifier,
) {
    if (itemsCount == 0) return
    val density = LocalDensity.current

    val layoutInfo = state.layoutInfo
    val firstVisible = state.firstVisibleItemIndex
    val visibleCount = layoutInfo.visibleItemsInfo.size

    if (visibleCount == 0 || itemsCount <= visibleCount) return

    val scrollFraction = remember(firstVisible, itemsCount) {
        (firstVisible.toFloat() / itemsCount).coerceIn(0f, 1f)
    }

    var trackHeightPx by remember { mutableStateOf(1f) }
    val coroutineScope = rememberCoroutineScope()

    val handleHeightDp = 32.dp
    val handleHeightPx = with(density) { handleHeightDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onGloballyPositioned { coordinates ->
                trackHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(itemsCount) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {},
                    onDragEnd = {},
                    onDragCancel = {},
                    onDrag = { change, _ ->
                        val y = change.position.y
                        val fraction = (y / trackHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * itemsCount).toInt().coerceIn(0, itemsCount - 1)
                        coroutineScope.launch {
                            state.scrollToItem(targetIndex)
                        }
                    }
                )
            }
    ) {
        // Track
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(
                    color = StratumThemeAccessor.colors.borderSubtle.copy(alpha = 0.5f)
                )
        )

        // Accent pill handle
        val maxOffsetPx = trackHeightPx - handleHeightPx
        val handleOffsetPx = scrollFraction * maxOffsetPx
        val handleOffsetDp = with(density) { handleOffsetPx.toDp() }

        Box(
            modifier = Modifier
                .offset(y = handleOffsetDp)
                .padding(end = 2.dp)
                .width(6.dp)
                .height(handleHeightDp)
                .background(
                    color = StratumThemeAccessor.colors.accent,
                    shape = RoundedCornerShape(3.dp)
                )
                .align(Alignment.TopEnd)
        )
    }
}
