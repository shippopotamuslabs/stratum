package com.stratum

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.FilterDropdownRow
import com.stratum.core.ui.component.LiftPickerSearchBottomBar
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapOptionRow
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.MuscleFilterBottomSheet
import com.stratum.core.ui.component.SelectedLiftsListPanel
import com.stratum.core.ui.component.SelectedLiftsTray
import com.stratum.core.ui.component.SelectedMusclesRow
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumSearchField
import com.stratum.core.ui.component.StratumSwipeToRevealRow
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.rememberGlassChromePadding
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.liftconfig.LiftConfigBottomSheet
import com.stratum.liftconfig.MergeTargetBottomSheet

@Composable
fun GymScreen(
    onCreateNewLift: () -> Unit,
    onManageEquipment: () -> Unit = {},
    onNavigateToIncrements: (liftId: String, equipmentId: String?) -> Unit = { _, _ -> },
    onNavigateToInformation: (liftId: String) -> Unit = {},
    onMergeModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GymViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )

    LaunchedEffect(state.isMergeMode) {
        onMergeModeChange(state.isMergeMode)
    }
    DisposableEffect(Unit) {
        onDispose {
            onMergeModeChange(false)
            viewModel.exitMergeMode()
        }
    }

    GymScreen(
        state = state,
        callbacks =
            GymScreenCallbacks(
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onFilterIntent = viewModel::onFilterIntent,
                onLiftEnabledChange = viewModel::toggleLiftEnabled,
                onNavigateToIncrements = onNavigateToIncrements,
                onNavigateToInformation = onNavigateToInformation,
                onCreateNewLift = onCreateNewLift,
                onManageEquipment = onManageEquipment,
                onEnterMergeMode = viewModel::enterMergeMode,
                onExitMergeMode = viewModel::exitMergeMode,
                onToggleMergeSelection = viewModel::toggleMergeSelection,
                onDeselectFromMerge = viewModel::deselectFromMerge,
                onConfirmMerge = viewModel::confirmMerge,
                onStartDelete = viewModel::onStartDelete,
                onConfirmDelete = viewModel::onConfirmDelete,
                onDismissDelete = viewModel::onDismissDelete,
            ),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun GymScreen(
    state: GymUiState,
    callbacks: GymScreenCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val onSearchQueryChanged = callbacks.onSearchQueryChanged
    val onFilterIntent = callbacks.onFilterIntent
    val onLiftEnabledChange = callbacks.onLiftEnabledChange
    val onNavigateToIncrements = callbacks.onNavigateToIncrements
    val onNavigateToInformation = callbacks.onNavigateToInformation
    val onCreateNewLift = callbacks.onCreateNewLift
    val onManageEquipment = callbacks.onManageEquipment
    val onEnterMergeMode = callbacks.onEnterMergeMode
    val onExitMergeMode = callbacks.onExitMergeMode
    val onToggleMergeSelection = callbacks.onToggleMergeSelection
    val onDeselectFromMerge = callbacks.onDeselectFromMerge
    val onConfirmMerge = callbacks.onConfirmMerge
    val onStartDelete = callbacks.onStartDelete
    val onConfirmDelete = callbacks.onConfirmDelete
    val onDismissDelete = callbacks.onDismissDelete
    val sheet = state.sheet
    var showMuscleSheet by remember { mutableStateOf(false) }
    var showGymActions by remember { mutableStateOf(false) }
    var selectedLift by remember { mutableStateOf<LiftSwapOptionState?>(null) }
    var showMergeTargetSheet by remember { mutableStateOf(false) }
    var showSelectedPanel by remember { mutableStateOf(false) }

    LaunchedEffect(state.sheet.hasSelection) {
        if (!state.sheet.hasSelection) {
            showSelectedPanel = false
        }
    }

    BackHandler(enabled = state.isMergeMode) {
        when {
            showMergeTargetSheet -> showMergeTargetSheet = false
            showSelectedPanel -> showSelectedPanel = false
            else -> onExitMergeMode()
        }
    }

    val listState = rememberLazyListState()
    val chrome = rememberGlassChromePadding()
    // Overlay only outside merge mode: the merge top bar stays solid, so its
    // content (the selection panel) must not slide beneath it.
    val overlayTopBar = chrome.active && !state.isMergeMode

    StratumTabScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        overlayTopBar = overlayTopBar,
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        topBar = {
            if (state.isMergeMode) {
                MergeModeTopBar(
                    selectedOptions = sheet.selectedOptions,
                    showSelectedPanel = showSelectedPanel,
                    canConfirm = state.canConfirmMerge,
                    onExpandToggle = { showSelectedPanel = !showSelectedPanel },
                    onCancel = onExitMergeMode,
                    onConfirmSelection = { showMergeTargetSheet = true },
                )
            } else {
                StratumTopBar(
                    titleContent = {
                        StratumSearchField(
                            value = sheet.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = StratumSpacing.s4),
                            placeholder = stringResource(UiR.string.lift_swap_search_placeholder),
                        )
                    },
                    actions = {
                        StratumIconButton(
                            onClick = { showGymActions = true },
                            containerColor = Color.Transparent,
                        ) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_more_vert),
                                contentDescription = stringResource(UiR.string.gym_more_actions),
                                tint = StratumThemeAccessor.colors.textPrimary,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.isMergeMode && !showSelectedPanel) {
                LiftPickerSearchBottomBar(
                    searchQuery = sheet.searchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                )
            }
        },
    ) {
        if (state.isMergeMode && showSelectedPanel) {
            SelectedLiftsListPanel(
                selectedOptions = sheet.selectedOptions,
                onDeselectLift = onDeselectFromMerge,
                modifier = Modifier.weight(1f),
            )
        } else {
            val selectedIds = if (state.isMergeMode) sheet.selectedLiftIds else emptySet()

            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                contentPadding =
                    PaddingValues(
                        top = if (overlayTopBar) chrome.top else 0.dp,
                        bottom = chrome.bottom + StratumSpacing.s16,
                    ),
            ) {
                item {
                    FilterDropdownRow(
                        modifier = Modifier.padding(top = StratumSpacing.s8, bottom = StratumSpacing.s4),
                        state = sheet,
                        onFilterIntent = onFilterIntent,
                        onMuscleFilterClick = { showMuscleSheet = true },
                    )
                }

                item {
                    SelectedMusclesRow(
                        selectedMuscles = sheet.selectedMuscles,
                        onMuscleToggled = { muscle -> onFilterIntent(LiftSwapFilterIntent.ToggleMuscle(muscle)) },
                        modifier = Modifier.padding(bottom = StratumSpacing.s4),
                    )
                }

                items(sheet.options, key = { option -> option.id }) { option ->
                    GymCatalogRow(
                        option = option,
                        selected = option.id in selectedIds,
                        isMergeMode = state.isMergeMode,
                        isSwipeEnabled = !state.isMerging && selectedLift?.id != option.id,
                        onToggleMergeSelection = onToggleMergeSelection,
                        onSelect = { selectedLift = option },
                        onStartDelete = onStartDelete,
                    )
                }

                if (sheet.options.isEmpty()) {
                    item(key = "empty-state") {
                        StratumText(
                            text = stringResource(UiR.string.lift_swap_empty),
                            muted = true,
                            modifier = Modifier.padding(vertical = StratumSpacing.s24),
                        )
                    }
                }
            }
        }
    }

    if (showGymActions) {
        GymActionsBottomSheet(
            onDismiss = { showGymActions = false },
            onAddLift = {
                showGymActions = false
                onCreateNewLift()
            },
            onManageEquipment = {
                showGymActions = false
                onManageEquipment()
            },
        )
    }

    if (showMuscleSheet) {
        MuscleFilterBottomSheet(
            onDismissRequest = { showMuscleSheet = false },
            selectedMuscles = sheet.selectedMuscles,
            muscleGroups = sheet.muscleGroups,
            onMuscleToggled = { muscle -> onFilterIntent(LiftSwapFilterIntent.ToggleMuscle(muscle)) },
            onClearAll = { onFilterIntent(LiftSwapFilterIntent.ResetMuscles) },
        )
    }

    val configLift = selectedLift
    if (configLift != null && !state.isMergeMode) {
        // Keep switch state live as the catalog updates while the sheet is open.
        val liveOption = sheet.options.firstOrNull { it.id == configLift.id } ?: configLift
        LiftConfigBottomSheet(
            liftName = liveOption.name,
            enabled = liveOption.enabled,
            onEnabledChange = { enabled -> onLiftEnabledChange(liveOption.id, enabled) },
            onIncrementsClick = {
                val option = liveOption
                selectedLift = null
                onNavigateToIncrements(option.id, option.equipmentId)
            },
            onInformationClick = {
                val id = liveOption.id
                selectedLift = null
                onNavigateToInformation(id)
            },
            onMergeWithLiftsClick = {
                val id = liveOption.id
                selectedLift = null
                onEnterMergeMode(id)
            },
            onDismissRequest = { selectedLift = null },
        )
    }

    val pendingDelete = state.pendingDelete
    if (pendingDelete != null) {
        LiftDeleteConfirmDialog(
            pending = pendingDelete,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }

    if (showMergeTargetSheet && state.isMergeMode && sheet.selectedOptions.size >= 2) {
        MergeTargetBottomSheet(
            candidates = sheet.selectedOptions,
            confirmEnabled = !state.isMerging,
            onConfirm = { targetId ->
                onConfirmMerge(targetId)
                // Keep sheet open while isMerging so double-confirm is blocked; close when done.
            },
            onDismissRequest = {
                if (!state.isMerging) showMergeTargetSheet = false
            },
        )
    }

    LaunchedEffect(state.isMerging, state.isMergeMode) {
        if (!state.isMerging && !state.isMergeMode) {
            showMergeTargetSheet = false
        }
    }
}

/**
 * One Gym catalog row: merge mode renders the plain selectable row; normal
 * mode wraps it in the STR-131 swipe-to-delete reveal with the STR-130 corner
 * treatment (transparent content background + panel clip, so the red reveal
 * conforms to the card's rounded corners). The revealed action opens the
 * delete confirmation — it never deletes directly.
 */
@Composable
private fun GymCatalogRow(
    option: LiftSwapOptionState,
    selected: Boolean,
    isMergeMode: Boolean,
    isSwipeEnabled: Boolean,
    onToggleMergeSelection: (LiftSwapOptionState) -> Unit,
    onSelect: () -> Unit,
    onStartDelete: (String) -> Unit,
) {
    val rowContent: @Composable () -> Unit = {
        LiftSwapOptionRow(
            option = option,
            selected = selected,
            onClick = {
                if (isMergeMode) {
                    onToggleMergeSelection(option)
                } else {
                    onSelect()
                }
            },
            trailingContent =
                if (isMergeMode) {
                    null
                } else {
                    {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = StratumThemeAccessor.colors.textMuted,
                        )
                    }
                },
        )
    }
    if (isMergeMode) {
        rowContent()
    } else {
        StratumSwipeToRevealRow(
            onActionClick = { onStartDelete(option.id) },
            actionContentDescription =
                stringResource(UiR.string.gym_delete_lift_action, option.name),
            enabled = isSwipeEnabled,
            contentBackgroundColor = Color.Transparent,
            modifier = Modifier.clip(StratumThemeAccessor.shapes.panel),
        ) {
            rowContent()
        }
    }
}

@Composable
private fun MergeModeTopBar(
    selectedOptions: List<LiftSwapOptionState>,
    showSelectedPanel: Boolean,
    canConfirm: Boolean,
    onExpandToggle: () -> Unit,
    onCancel: () -> Unit,
    onConfirmSelection: () -> Unit,
) {
    StratumTopBar(
        titleContent = {
            if (selectedOptions.isNotEmpty()) {
                SelectedLiftsTray(
                    selectedOptions = selectedOptions,
                    expanded = showSelectedPanel,
                    onExpandToggle = onExpandToggle,
                )
            }
        },
        navigationIcon = {
            StratumIconButton(
                onClick = onCancel,
                containerColor = Color.Transparent,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_close),
                    contentDescription = stringResource(UiR.string.gym_merge_cancel),
                    tint = StratumThemeAccessor.colors.textPrimary,
                )
            }
        },
        actions = {
            // Keep enabled so disabledContainerColor does not fill the chrome (matches X).
            StratumIconButton(
                onClick = {
                    if (canConfirm) onConfirmSelection()
                },
                containerColor = Color.Transparent,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_check),
                    contentDescription = stringResource(UiR.string.gym_merge_confirm_selection),
                    tint =
                        if (canConfirm) {
                            StratumThemeAccessor.colors.textPrimary
                        } else {
                            StratumThemeAccessor.colors.textMuted
                        },
                )
            }
        },
    )
}

@Composable
private fun GymActionsBottomSheet(
    onDismiss: () -> Unit,
    onAddLift: () -> Unit,
    onManageEquipment: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.gym_actions_title),
    ) {
        GymActionRow(
            label = stringResource(UiR.string.lift_swap_create_new_lift),
            leadingIconRes = UiR.drawable.ic_add,
            onClick = onAddLift,
        )
        GymActionRow(
            label = stringResource(UiR.string.gym_manage_equipment),
            leadingIcon = Icons.Outlined.FitnessCenter,
            onClick = onManageEquipment,
        )
    }
}

/** Bordered sheet row: raised surface, subtle border, leading icon + label. */
@Composable
private fun GymActionRow(
    label: String,
    onClick: () -> Unit,
    @DrawableRes leadingIconRes: Int? = null,
    leadingIcon: ImageVector? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s16),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leadingIcon != null ->
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            leadingIconRes != null ->
                Icon(
                    painter = painterResource(leadingIconRes),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
        }
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Callbacks for the pure [GymScreen] composable — keeps the parameter list under Detekt's
 * threshold (mirrors [com.stratum.navigation.MainTabShellCallbacks]).
 */
data class GymScreenCallbacks(
    val onSearchQueryChanged: (String) -> Unit,
    val onFilterIntent: (LiftSwapFilterIntent) -> Unit,
    val onLiftEnabledChange: (String, Boolean) -> Unit,
    val onNavigateToIncrements: (liftId: String, equipmentId: String?) -> Unit,
    val onNavigateToInformation: (String) -> Unit,
    val onCreateNewLift: () -> Unit,
    val onManageEquipment: () -> Unit = {},
    val onEnterMergeMode: (originLiftId: String) -> Unit = {},
    val onExitMergeMode: () -> Unit = {},
    val onToggleMergeSelection: (LiftSwapOptionState) -> Unit = {},
    val onDeselectFromMerge: (String) -> Unit = {},
    val onConfirmMerge: (targetLiftId: String) -> Unit = {},
    val onStartDelete: (liftId: String) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
)
