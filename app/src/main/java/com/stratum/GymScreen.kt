package com.stratum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.SelectedMusclesRow
import com.stratum.core.ui.component.MuscleFilterBottomSheet
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumSearchField
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.liftconfig.LiftConfigBottomSheet

@Composable
fun GymScreen(
    onCreateNewLift: () -> Unit,
    onNavigateToIncrements: (liftId: String) -> Unit = {},
    onNavigateToInformation: (liftId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GymViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GymScreen(
        state = state,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onFilterIntent = viewModel::onFilterIntent,
        onLiftEnabledChange = viewModel::toggleLiftEnabled,
        onNavigateToIncrements = onNavigateToIncrements,
        onNavigateToInformation = onNavigateToInformation,
        onCreateNewLift = onCreateNewLift,
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
    modifier: Modifier = Modifier,
) {
    var showMuscleSheet by remember { mutableStateOf(false) }
    var selectedLift by remember { mutableStateOf<LiftSwapOptionState?>(null) }

    StratumTabScaffold(
        modifier = modifier,
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
                        onClick = onCreateNewLift,
                        containerColor = Color.Transparent,
                    ) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_add),
                            contentDescription = stringResource(UiR.string.lift_swap_create_new_lift),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                },
            )
        },
    ) {
        FilterDropdownRow(
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
