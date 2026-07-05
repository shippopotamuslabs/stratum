package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumDropdownMenu
import com.stratum.core.ui.component.StratumDropdownMenuItem
import com.stratum.core.ui.component.StratumDurationStepper
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.component.StratumMuscleVolumeRow
import com.stratum.core.ui.component.StratumVolumeBodyTier
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.EditWorkoutViewModel
import com.stratum.feature.program.R
import com.stratum.feature.program.SupersetLinkDirection
import com.stratum.feature.program.formatEstimatedWorkoutDuration

private const val MIN_REST_SECONDS = 0
private const val MAX_REST_SECONDS = 600
private const val REORDER_DRAG_THRESHOLD_DP = 48f

@Composable
fun EditWorkoutRoute(
    workoutId: String,
    onBackClick: () -> Unit,
    onSwapLiftClick: (workoutLiftId: String, liftId: String, liftName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditWorkoutViewModel = hiltViewModel(),
) {
    LaunchedEffect(workoutId) {
        viewModel.initialize(workoutId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedState = state as? EditWorkoutUiState.Loaded

    EditWorkoutScreen(
        state = state,
        onBackClick = {
            if (loadedState?.isReorderMode == true) {
                viewModel.cancelReorderMode()
            } else {
                onBackClick()
            }
        },
        callbacks =
            EditWorkoutScreenCallbacks(
                onToggleReorderMode = viewModel::toggleReorderMode,
                onConfirmReorder = viewModel::confirmReorder,
                onReorderLifts = viewModel::reorderLifts,
                onAddSet = viewModel::addSet,
                onRemoveSet = viewModel::removeSet,
                onAddLift = viewModel::addLift,
                onRemoveLift = viewModel::removeLift,
                onLinkSupersetWithPrevious = viewModel::linkSupersetWithPrevious,
                onLinkSupersetWithNext = viewModel::linkSupersetWithNext,
                onRemoveFromSuperset = viewModel::removeFromSuperset,
                onSetRestSeconds = viewModel::setRestSeconds,
                onSetRestSecondsForAllSetsOnLift = viewModel::setRestSecondsForAllSetsOnLift,
                onSetRepMin = viewModel::setRepMin,
                onSetRepMax = viewModel::setRepMax,
                onSetRirGoal = viewModel::setRirGoal,
                onSwapLiftClick = onSwapLiftClick,
            ),
        modifier = modifier,
    )
}

@Composable
fun EditWorkoutScreen(
    state: EditWorkoutUiState,
    onBackClick: () -> Unit,
    callbacks: EditWorkoutScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    var restSheetSet by remember { mutableStateOf<EditSetState?>(null) }
    var restSheetLiftId by remember { mutableStateOf<String?>(null) }
    var restSheetSeconds by remember { mutableIntStateOf(DEFAULT_REST_SECONDS) }
    var rirSheetSet by remember { mutableStateOf<EditSetState?>(null) }
    var supersetSheetLiftId by remember { mutableStateOf<String?>(null) }

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            EditWorkoutTopBar(
                state = state,
                onBackClick = onBackClick,
                onToggleReorderMode = callbacks.onToggleReorderMode,
                onConfirmReorder = callbacks.onConfirmReorder,
            )
        },
    ) { padding ->
        when (state) {
            EditWorkoutUiState.Loading ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(
                        text = stringResource(R.string.edit_workout_loading),
                        muted = true,
                    )
                }

            EditWorkoutUiState.NotFound ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(
                        text = stringResource(R.string.edit_workout_not_found),
                        muted = true,
                    )
                }

            is EditWorkoutUiState.Loaded ->
                if (state.isReorderMode) {
                    EditWorkoutReorderList(
                        lifts = state.pendingReorderLifts.orEmpty(),
                        onReorderLifts = callbacks.onReorderLifts,
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    EditWorkoutContent(
                        state = state,
                        callbacks = callbacks,
                        onRestClick = { liftId, set ->
                            restSheetSet = set
                            restSheetLiftId = liftId
                            restSheetSeconds = set.restSeconds
                        },
                        onRirClick = { set -> rirSheetSet = set },
                        onCreateSupersetClick = { liftId -> supersetSheetLiftId = liftId },
                        modifier = Modifier.padding(padding),
                    )
                }
        }
    }

    restSheetSet?.let { targetSet ->
        val liftId = restSheetLiftId
        if (liftId != null) {
            RestTimeBottomSheet(
                totalSeconds = restSheetSeconds,
                onTotalSecondsChange = { seconds -> restSheetSeconds = seconds },
                onApplyToSet = {
                    callbacks.onSetRestSeconds(targetSet.setId, restSheetSeconds)
                    restSheetSet = null
                    restSheetLiftId = null
                },
                onApplyToAllSets = {
                    callbacks.onSetRestSecondsForAllSetsOnLift(liftId, restSheetSeconds)
                    restSheetSet = null
                    restSheetLiftId = null
                },
                onDismiss = {
                    restSheetSet = null
                    restSheetLiftId = null
                },
            )
        }
    }

    rirSheetSet?.let { targetSet ->
        RirTargetBottomSheet(
            selectedRirGoal = targetSet.rirGoal,
            onRirSelected = { rir -> callbacks.onSetRirGoal(targetSet.setId, rir) },
            onDismiss = { rirSheetSet = null },
        )
    }

    val loadedState = state as? EditWorkoutUiState.Loaded
    supersetSheetLiftId?.let { liftId ->
        val options =
            loadedState?.let { loaded ->
                supersetLinkSheetOptions(lifts = loaded.lifts, workoutLiftId = liftId)
            }.orEmpty()
        if (options.isNotEmpty()) {
            SupersetLinkBottomSheet(
                options = options,
                onOptionSelected = { direction ->
                    when (direction) {
                        SupersetLinkDirection.Previous -> callbacks.onLinkSupersetWithPrevious(liftId)
                        SupersetLinkDirection.Next -> callbacks.onLinkSupersetWithNext(liftId)
                        SupersetLinkDirection.Remove -> callbacks.onRemoveFromSuperset(liftId)
                    }
                },
                onDismiss = { supersetSheetLiftId = null },
            )
        } else {
            supersetSheetLiftId = null
        }
    }
}

@Composable
private fun EditWorkoutTopBar(
    state: EditWorkoutUiState,
    onBackClick: () -> Unit,
    onToggleReorderMode: () -> Unit,
    onConfirmReorder: () -> Unit,
) {
    val title =
        when (state) {
            is EditWorkoutUiState.Loaded -> state.workoutName
            else -> stringResource(R.string.edit_workout_title)
        }
    val isReorderMode = state is EditWorkoutUiState.Loaded && state.isReorderMode

    StratumTopBar(
        title = title,
        navigationIcon = {
            StratumTopBarBackButton(
                onClick = onBackClick,
                contentDescription = stringResource(R.string.edit_workout_back),
            )
        },
        actions = {
            if (state is EditWorkoutUiState.Loaded) {
                if (isReorderMode) {
                    StratumIconButton(onClick = onConfirmReorder) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_check),
                            contentDescription = stringResource(R.string.edit_workout_confirm_reorder),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                } else {
                    StratumIconButton(onClick = onToggleReorderMode) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_swap_vert),
                            contentDescription = stringResource(R.string.edit_workout_reorder_lifts),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun EditWorkoutContent(
    state: EditWorkoutUiState.Loaded,
    callbacks: EditWorkoutScreenCallbacks,
    onRestClick: (workoutLiftId: String, EditSetState) -> Unit,
    onRirClick: (EditSetState) -> Unit,
    onCreateSupersetClick: (workoutLiftId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.upperVolumeByMuscle.isNotEmpty() || state.lowerVolumeByMuscle.isNotEmpty()) {
            item {
                val initialTier =
                    if (state.upperVolumeByMuscle.isEmpty() && state.lowerVolumeByMuscle.isNotEmpty()) {
                        StratumVolumeBodyTier.Lower
                    } else {
                        StratumVolumeBodyTier.Upper
                    }
                StratumMuscleVolumeRow(
                    upperCards = state.upperVolumeByMuscle,
                    lowerCards = state.lowerVolumeByMuscle,
                    initialTier = initialTier,
                )
            }
        }
        item {
            EditWorkoutExerciseSummaryHeader(
                exerciseCount = state.lifts.size,
                estimatedDurationMinutes = state.estimatedDurationMinutes,
            )
        }
        itemsIndexed(state.lifts, key = { _, lift -> lift.workoutLiftId }) { _, lift ->
            EditLiftPanel(
                lift = lift,
                allLifts = state.lifts,
                onAddSet = { callbacks.onAddSet(lift.workoutLiftId) },
                onRemoveSet = callbacks.onRemoveSet,
                onRemoveLift = { callbacks.onRemoveLift(lift.workoutLiftId) },
                onCreateSupersetClick = { onCreateSupersetClick(lift.workoutLiftId) },
                onSwapLiftClick = {
                    callbacks.onSwapLiftClick(lift.workoutLiftId, lift.liftId, lift.name)
                },
                onRestClick = onRestClick,
                onRirClick = onRirClick,
                onSetRepMin = callbacks.onSetRepMin,
                onSetRepMax = callbacks.onSetRepMax,
            )
        }
        item {
            StratumOutlinedButton(
                text = stringResource(R.string.edit_workout_add_exercise),
                onClick = callbacks.onAddLift,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditWorkoutExerciseSummaryHeader(
    exerciseCount: Int,
    estimatedDurationMinutes: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StratumText(
            text = stringResource(R.string.edit_workout_exercise_count_header, exerciseCount),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumText(
            text =
                stringResource(
                    R.string.edit_workout_estimated_duration,
                    formatEstimatedWorkoutDuration(estimatedDurationMinutes),
                ),
            muted = true,
        )
    }
}

@Composable
private fun EditLiftPanel(
    lift: EditLiftState,
    allLifts: List<EditLiftState>,
    onAddSet: () -> Unit,
    onRemoveSet: (setId: String) -> Unit,
    onRemoveLift: () -> Unit,
    onCreateSupersetClick: () -> Unit,
    onSwapLiftClick: () -> Unit,
    onRestClick: (workoutLiftId: String, EditSetState) -> Unit,
    onRirClick: (EditSetState) -> Unit,
    onSetRepMin: (setId: String, repMin: Int) -> Unit,
    onSetRepMax: (setId: String, repMax: Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = lift.name,
                style = StratumThemeAccessor.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                StratumIconButton(
                    onClick = { menuExpanded = true },
                    containerColor = Color.Transparent,
                ) {
                    Icon(
                        painter = painterResource(CoreUiR.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.edit_workout_lift_menu),
                        tint = StratumThemeAccessor.colors.textPrimary,
                    )
                }
                StratumDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    StratumDropdownMenuItem(
                        text = stringResource(R.string.edit_workout_create_superset),
                        onClick = {
                            menuExpanded = false
                            onCreateSupersetClick()
                        },
                    )
                    StratumDropdownMenuItem(
                        text = stringResource(R.string.edit_workout_swap_lift),
                        onClick = {
                            menuExpanded = false
                            onSwapLiftClick()
                        },
                    )
                    StratumDropdownMenuItem(
                        text = stringResource(R.string.edit_workout_remove_lift),
                        onClick = {
                            menuExpanded = false
                            onRemoveLift()
                        },
                        isDestructive = true,
                    )
                }
            }
        }

        if (lift.showsSupersetChip(allLifts)) {
            lift.supersetGroupId?.let { groupId ->
                SupersetGroupChip(groupId = groupId)
            }
        }

        EditWorkoutSetTable(
            sets = lift.sets,
            onAddSet = onAddSet,
            onRemoveSet = onRemoveSet,
            onRepMinChange = onSetRepMin,
            onRepMaxChange = onSetRepMax,
            onRestClick = { set -> onRestClick(lift.workoutLiftId, set) },
            onRirClick = onRirClick,
        )
    }
}

@Composable
private fun EditWorkoutReorderList(
    lifts: List<EditLiftReorderState>,
    onReorderLifts: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(lifts, key = { _, lift -> lift.workoutLiftId }) { index, lift ->
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
                        .pointerInput(lifts.size) {
                            detectDragGesturesAfterLongPress(
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
                                    if (draggedIndex < 0) return@detectDragGesturesAfterLongPress
                                    dragOffset += dragAmount.y
                                    val thresholdPx = REORDER_DRAG_THRESHOLD_DP * density
                                    while (dragOffset > thresholdPx && draggedIndex < lifts.lastIndex) {
                                        onReorderLifts(draggedIndex, draggedIndex + 1)
                                        draggedIndex += 1
                                        dragOffset -= thresholdPx
                                    }
                                    while (dragOffset < -thresholdPx && draggedIndex > 0) {
                                        onReorderLifts(draggedIndex, draggedIndex - 1)
                                        draggedIndex -= 1
                                        dragOffset += thresholdPx
                                    }
                                },
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_swap_vert),
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textMuted,
                )
                StratumText(
                    text = lift.name,
                    style = StratumThemeAccessor.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RestTimeBottomSheet(
    totalSeconds: Int,
    onTotalSecondsChange: (Int) -> Unit,
    onApplyToSet: () -> Unit,
    onApplyToAllSets: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetColor = StratumThemeAccessor.colors.surfaceRaised
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.edit_workout_rest_sheet_title),
    ) {
        StratumDurationStepper(
            totalMinutes = totalSeconds,
            onTotalMinutesChange = onTotalSecondsChange,
            minMinutes = MIN_REST_SECONDS,
            maxMinutes = MAX_REST_SECONDS,
            hoursLabel = stringResource(R.string.edit_workout_rest_minutes_label),
            minutesLabel = stringResource(R.string.edit_workout_rest_seconds_label),
            incrementContentDescription = stringResource(R.string.edit_workout_rest_increment),
            decrementContentDescription = stringResource(R.string.edit_workout_rest_decrement),
            modifier = Modifier.fillMaxWidth(),
        )
        StratumButton(
            text = stringResource(R.string.edit_workout_rest_apply_to_set),
            onClick = onApplyToSet,
            modifier = Modifier.fillMaxWidth(),
        )
        StratumOutlinedButton(
            text = stringResource(R.string.edit_workout_rest_apply_to_all_sets),
            onClick = onApplyToAllSets,
            containerColor = sheetColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Edit workout loaded light")
@Composable
fun EditWorkoutScreenLoadedPreview() {
    StratumTheme {
        EditWorkoutScreen(
            state =
                EditWorkoutUiState.Loaded(
                    workoutId = "w-1",
                    workoutName = "Push Day",
                    weekdayLabel = "Monday",
                    upperVolumeByMuscle =
                        listOf(
                            StratumMuscleVolumeCardState(
                                muscle = MuscleGroup.Chest,
                                displayName = "Chest",
                                exerciseCount = 1,
                                setCount = 2.0,
                            ),
                        ),
                    lowerVolumeByMuscle = emptyList(),
                    estimatedDurationMinutes = 4,
                    lifts =
                        listOf(
                            EditLiftState(
                                workoutLiftId = "wl-1",
                                liftId = "bench-press",
                                name = "Barbell Bench Press",
                                position = 0,
                                sets =
                                    listOf(
                                        EditSetState(
                                            setId = "s-1",
                                            setNumber = 1,
                                            repMin = 5,
                                            repMax = 5,
                                            restSeconds = 120,
                                            rirGoal = 2,
                                        ),
                                        EditSetState(
                                            setId = "s-2",
                                            setNumber = 2,
                                            repMin = 8,
                                            repMax = 12,
                                            restSeconds = 120,
                                            rirGoal = 2,
                                        ),
                                    ),
                            ),
                        ),
                ),
            onBackClick = {},
            callbacks = EditWorkoutScreenCallbacks(),
        )
    }
}

@Preview(name = "Edit workout reorder light")
@Composable
fun EditWorkoutScreenReorderPreview() {
    StratumTheme {
        EditWorkoutScreen(
            state =
                EditWorkoutUiState.Loaded(
                    workoutId = "w-1",
                    workoutName = "Push Day",
                    weekdayLabel = "Monday",
                    lifts = emptyList(),
                    isReorderMode = true,
                    pendingReorderLifts =
                        listOf(
                            EditLiftReorderState("wl-1", "Barbell Bench Press"),
                            EditLiftReorderState("wl-2", "Incline Dumbbell Press"),
                        ),
                ),
            onBackClick = {},
            callbacks = EditWorkoutScreenCallbacks(),
        )
    }
}
