package com.stratum.feature.program.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R
import com.stratum.core.ui.component.FilterDropdownRow
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapOptionRow
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.core.ui.component.MuscleFilterBottomSheet
import com.stratum.core.ui.component.LiftPickerSearchBottomBar
import com.stratum.core.ui.component.SelectedLiftsListPanel
import com.stratum.core.ui.component.SelectedLiftsTray
import com.stratum.core.ui.component.SelectedMusclesRow
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumSwitch
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.LiftSwapViewModel
import com.stratum.feature.program.navigation.LiftSwapCommitMode

/**
 * Route-level entry point for the Lift Swap screen (ARCHITECTURE.md v1.0).
 * Connects Hilt ViewModel with the screen.
 */
@Composable
fun LiftSwapRoute(
    workoutId: String,
    workoutLiftId: String?,
    liftId: String?,
    liftName: String?,
    onBackClick: () -> Unit,
    onCreateNewLift: () -> Unit,
    modifier: Modifier = Modifier,
    commitMode: LiftSwapCommitMode = LiftSwapCommitMode.EditSession,
    viewModel: LiftSwapViewModel = hiltViewModel(),
) {
    LaunchedEffect(workoutId, workoutLiftId, liftId, liftName, commitMode) {
        viewModel.initialize(
            workoutId = workoutId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            liftName = liftName,
            commitMode = commitMode,
        )
    }

    // Clear selection if the user leaves without confirming (retained ViewModel).
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearSelection() }
    }

    val state by viewModel.liftSwapState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )

    state?.let { activeState ->
        LiftSwapScreen(
            state = activeState,
            onLiftClicked = { option ->
                viewModel.onLiftClicked(option = option, onSwapComplete = onBackClick)
            },
            onDeselectLift = viewModel::deselectLift,
            onConfirmSelections = {
                viewModel.confirmSelections(onComplete = onBackClick)
            },
            onAddAsSupersetChanged = viewModel::setAddAsSuperset,
            onCreateNewLift = onCreateNewLift,
            onBackClick = onBackClick,
            onFilterIntent = viewModel::onFilterIntent,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )
    }
}

/**
 * Screen presenting swap candidates for the targeted exercise, with deferred multi-select
 * confirmation and an expandable selected-lifts panel.
 */
@Composable
fun LiftSwapScreen(
    state: LiftSwapSheetState,
    onLiftClicked: (LiftSwapOptionState) -> Unit,
    onDeselectLift: (String) -> Unit,
    onConfirmSelections: () -> Unit,
    onAddAsSupersetChanged: (Boolean) -> Unit,
    onCreateNewLift: () -> Unit,
    onBackClick: () -> Unit,
    onFilterIntent: (LiftSwapFilterIntent) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    var allLiftsExpanded by remember { mutableStateOf(false) }
    var showMuscleSheet by remember { mutableStateOf(false) }
    var showSelectedPanel by remember { mutableStateOf(false) }
    val isDefaultScreen = !state.hasActiveFilter && state.searchQuery.isBlank()
    // Multi-select chrome is add-only; swap is one-tap commit.
    val selectedIds = if (state.isAddMode) state.selectedLiftIds else emptySet()

    // Collapse selected panel if the last selection is cleared while expanded.
    LaunchedEffect(state.hasSelection) {
        if (!state.hasSelection) {
            showSelectedPanel = false
        }
    }

    BackHandler(enabled = showSelectedPanel) {
        showSelectedPanel = false
    }

    // Glass (§12.8, STR-158): under the route's glass host the top bar overlays
    // and the panel region below carries the top clearance, so the picker
    // panels (and their lists) start beneath the glass; the search bottom bar
    // and IME behavior are untouched. Solid hosts keep the in-flow geometry.
    StratumDetailScaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                titleContent = {
                    if (state.isAddMode && state.hasSelection) {
                        // Replaces "Add Lift" so the tray does not push list content down.
                        SelectedLiftsTray(
                            selectedOptions = state.selectedOptions,
                            expanded = showSelectedPanel,
                            onExpandToggle = { showSelectedPanel = !showSelectedPanel },
                        )
                    } else {
                        StratumText(
                            text =
                                if (state.isAddMode) {
                                    stringResource(R.string.lift_add_title)
                                } else {
                                    stringResource(R.string.lift_swap_title, state.targetLiftName)
                                },
                            style = StratumThemeAccessor.typography.titleLarge,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = {
                            if (showSelectedPanel) {
                                showSelectedPanel = false
                            } else {
                                onBackClick()
                            }
                        },
                        contentDescription = "Back",
                    )
                },
                actions = {
                    StratumIconButton(
                        onClick = onCreateNewLift,
                        containerColor = Color.Transparent,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.lift_swap_create_new_lift),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!showSelectedPanel) {
                LiftPickerSearchBottomBar(
                    searchQuery = state.searchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                    showDone = state.isAddMode,
                    doneEnabled = state.hasSelection,
                    onDoneClick = onConfirmSelections,
                )
            }
        },
    ) { chrome ->
        AnimatedContent(
            targetState = showSelectedPanel && state.isAddMode,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = chrome.top),
            transitionSpec = {
                if (targetState) {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                        (slideOutVertically { height -> -height / 4 } + fadeOut())
                } else {
                    (slideInVertically { height -> -height / 4 } + fadeIn()) togetherWith
                        (slideOutVertically { height -> height } + fadeOut())
                }
            },
            label = "liftSwapPanel",
        ) { selectedPanelVisible ->
            if (selectedPanelVisible) {
                SelectedLiftsPanel(
                    state = state,
                    onDeselectLift = onDeselectLift,
                    onAddAsSupersetChanged = onAddAsSupersetChanged,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
                )
            } else {
                LiftPickerContent(
                    state = state,
                    selectedIds = selectedIds,
                    isDefaultScreen = isDefaultScreen,
                    allLiftsExpanded = allLiftsExpanded,
                    onAllLiftsExpandedChange = { allLiftsExpanded = it },
                    onLiftClicked = onLiftClicked,
                    onFilterIntent = onFilterIntent,
                    onMuscleFilterClick = { showMuscleSheet = true },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
                )
            }
        }
    }

    if (showMuscleSheet) {
        MuscleFilterBottomSheet(
            onDismissRequest = { showMuscleSheet = false },
            selectedMuscles = state.selectedMuscles,
            muscleGroups = state.muscleGroups,
            onMuscleToggled = { muscle -> onFilterIntent(LiftSwapFilterIntent.ToggleMuscle(muscle)) },
            onClearAll = { onFilterIntent(LiftSwapFilterIntent.ResetMuscles) },
        )
    }
}

@Composable
private fun LiftPickerContent(
    state: LiftSwapSheetState,
    selectedIds: Set<String>,
    isDefaultScreen: Boolean,
    allLiftsExpanded: Boolean,
    onAllLiftsExpandedChange: (Boolean) -> Unit,
    onLiftClicked: (LiftSwapOptionState) -> Unit,
    onFilterIntent: (LiftSwapFilterIntent) -> Unit,
    onMuscleFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        FilterDropdownRow(
            state = state,
            onFilterIntent = onFilterIntent,
            onMuscleFilterClick = onMuscleFilterClick,
        )

        SelectedMusclesRow(
            selectedMuscles = state.selectedMuscles,
            onMuscleToggled = { muscle -> onFilterIntent(LiftSwapFilterIntent.ToggleMuscle(muscle)) },
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
        ) {
            if (isDefaultScreen && !state.isAddMode) {
                val similarLifts = state.options.filter { it.similarity >= SIMILARITY_THRESHOLD }
                val remainingLifts = state.options.filter { it.similarity < SIMILARITY_THRESHOLD }

                if (similarLifts.isNotEmpty()) {
                    item(key = "header-similar") {
                        StratumText(
                            text = "Similar Lifts",
                            style = StratumThemeAccessor.typography.sectionLabel,
                            muted = true,
                            modifier = Modifier.padding(vertical = StratumSpacing.s8),
                        )
                    }
                    items(similarLifts, key = { option -> option.id }) { option ->
                        LiftSwapOptionRow(
                            option = option,
                            selected = option.id in selectedIds,
                            onClick = { onLiftClicked(option) },
                        )
                    }
                }

                if (remainingLifts.isNotEmpty()) {
                    item(key = "header-all-lifts") {
                        val rotation by animateFloatAsState(
                            targetValue = if (allLiftsExpanded) 180f else 0f,
                            label = "chevronRotation",
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onAllLiftsExpandedChange(!allLiftsExpanded) }
                                    .padding(vertical = StratumSpacing.s8),
                            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StratumText(
                                text = "All Lifts",
                                style = StratumThemeAccessor.typography.sectionLabel,
                                muted = true,
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_down),
                                contentDescription = null,
                                tint = StratumThemeAccessor.colors.textMuted,
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .rotate(rotation),
                            )
                        }
                    }

                    if (allLiftsExpanded) {
                        items(remainingLifts, key = { option -> option.id }) { option ->
                            LiftSwapOptionRow(
                                option = option,
                                selected = option.id in selectedIds,
                                onClick = { onLiftClicked(option) },
                            )
                        }
                    }
                }
            } else {
                items(state.options, key = { option -> option.id }) { option ->
                    LiftSwapOptionRow(
                        option = option,
                        selected = option.id in selectedIds,
                        onClick = { onLiftClicked(option) },
                    )
                }
            }

            if (state.options.isEmpty()) {
                item(key = "empty-state") {
                    StratumText(
                        text = stringResource(R.string.lift_swap_empty),
                        muted = true,
                        modifier = Modifier.padding(vertical = StratumSpacing.s24),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedLiftsPanel(
    state: LiftSwapSheetState,
    onDeselectLift: (String) -> Unit,
    onAddAsSupersetChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectedLiftsListPanel(
        selectedOptions = state.selectedOptions,
        onDeselectLift = onDeselectLift,
        modifier = modifier,
        header =
            if (state.isAddMode && state.selectedOptions.size >= 2) {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StratumText(
                            text =
                                if (state.selectedOptions.size > 2) {
                                    stringResource(R.string.lift_swap_add_as_giant_set)
                                } else {
                                    stringResource(R.string.lift_swap_add_as_superset)
                                },
                            style = StratumThemeAccessor.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        StratumSwitch(
                            checked = state.addAsSuperset,
                            onCheckedChange = onAddAsSupersetChanged,
                        )
                    }
                }
            } else {
                null
            },
    )
}

private const val SIMILARITY_THRESHOLD = 0.4f
