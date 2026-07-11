package com.stratum

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.FilterDropdownRow
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapOptionRow
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.core.ui.component.MuscleFilterBottomSheet
import com.stratum.core.ui.component.SelectedMusclesRow
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumSearchField
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.liftconfig.LiftConfigBottomSheet

@Composable
fun GymScreen(
    onCreateNewLift: () -> Unit,
    onManageEquipment: () -> Unit = {},
    onNavigateToIncrements: (liftId: String) -> Unit = {},
    onNavigateToInformation: (liftId: String) -> Unit = {},
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

    GymScreen(
        state = state,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onFilterIntent = viewModel::onFilterIntent,
        onLiftEnabledChange = viewModel::toggleLiftEnabled,
        onNavigateToIncrements = onNavigateToIncrements,
        onNavigateToInformation = onNavigateToInformation,
        onCreateNewLift = onCreateNewLift,
        onManageEquipment = onManageEquipment,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun GymScreen(
    state: LiftSwapSheetState,
    onSearchQueryChanged: (String) -> Unit,
    onFilterIntent: (LiftSwapFilterIntent) -> Unit,
    onLiftEnabledChange: (String, Boolean) -> Unit,
    onNavigateToIncrements: (String) -> Unit,
    onNavigateToInformation: (String) -> Unit,
    onCreateNewLift: () -> Unit,
    onManageEquipment: () -> Unit = {},
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    var showMuscleSheet by remember { mutableStateOf(false) }
    var showGymActions by remember { mutableStateOf(false) }
    var selectedLift by remember { mutableStateOf<LiftSwapOptionState?>(null) }

    StratumTabScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = {
            StratumTopBar(
                titleContent = {
                    StratumSearchField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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
        },
    ) {
        FilterDropdownRow(
            modifier = Modifier.padding(top = 8.dp),
            state = state,
            onFilterIntent = onFilterIntent,
            onMuscleFilterClick = { showMuscleSheet = true },
        )

        SelectedMusclesRow(
            selectedMuscles = state.selectedMuscles,
            onMuscleToggled = { muscle -> onFilterIntent(LiftSwapFilterIntent.ToggleMuscle(muscle)) },
        )

        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(state.options, key = { option -> option.id }) { option ->
                LiftSwapOptionRow(
                    option = option,
                    onClick = { selectedLift = option },
                    trailingContent = {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = StratumThemeAccessor.colors.textMuted,
                        )
                    },
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
            selectedMuscles = state.selectedMuscles,
            muscleGroups = state.muscleGroups,
            onMuscleToggled = { muscle -> onFilterIntent(LiftSwapFilterIntent.ToggleMuscle(muscle)) },
            onClearAll = { onFilterIntent(LiftSwapFilterIntent.ResetMuscles) },
        )
    }

    val configLift = selectedLift
    if (configLift != null) {
        // Keep switch state live as the catalog updates while the sheet is open.
        val liveOption = state.options.firstOrNull { it.id == configLift.id } ?: configLift
        LiftConfigBottomSheet(
            liftName = liveOption.name,
            enabled = liveOption.enabled,
            onEnabledChange = { enabled -> onLiftEnabledChange(liveOption.id, enabled) },
            onIncrementsClick = {
                val id = liveOption.id
                selectedLift = null
                onNavigateToIncrements(id)
            },
            onInformationClick = {
                val id = liveOption.id
                selectedLift = null
                onNavigateToInformation(id)
            },
            onDismissRequest = { selectedLift = null },
        )
    }
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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

