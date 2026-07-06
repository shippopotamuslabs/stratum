package com.stratum.feature.program.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.stratum.core.ui.R
import com.stratum.core.ui.component.FilterDropdownRow
import com.stratum.core.ui.component.LiftLibraryFilter
import com.stratum.core.ui.component.LiftSwapDropdownOption
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapMuscleTiers
import com.stratum.core.ui.component.LiftSwapOptionRow
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.core.ui.component.StratumDropdownMenu
import com.stratum.core.ui.component.StratumDropdownMenuItem
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.SelectedMusclesRow
import com.stratum.core.ui.component.MuscleFilterBottomSheet
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumSearchField
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.LiftCategory
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.LiftSwapViewModel

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
    viewModel: LiftSwapViewModel = hiltViewModel(),
) {
    LaunchedEffect(workoutId, workoutLiftId, liftId, liftName) {
        viewModel.initialize(workoutId, workoutLiftId, liftId, liftName)
    }

    val state by viewModel.liftSwapState.collectAsStateWithLifecycle()

    state?.let { activeState ->
        LiftSwapScreen(
            state = activeState,
            onLiftSelected = { newLiftId ->
                viewModel.selectLift(
                    newLiftId = newLiftId,
                    onComplete = onBackClick
                )
            },
            onCreateNewLift = onCreateNewLift,
            onBackClick = onBackClick,
            onFilterIntent = viewModel::onFilterIntent,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            modifier = modifier,
        )
    }
}

/**
 * Screen presenting swap candidates for the targeted exercise.
 */
@Composable
fun LiftSwapScreen(
    state: LiftSwapSheetState,
    onLiftSelected: (String) -> Unit,
    onCreateNewLift: () -> Unit,
    onBackClick: () -> Unit,
    onFilterIntent: (LiftSwapFilterIntent) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var allLiftsExpanded by remember { mutableStateOf(false) }
    var showMuscleSheet by remember { mutableStateOf(false) }
    val isDefaultScreen = !state.hasActiveFilter && state.searchQuery.isBlank()

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                title = if (state.isAddMode) {
                    stringResource(R.string.lift_add_title)
                } else {
                    stringResource(R.string.lift_swap_title, state.targetLiftName)
                },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
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
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(similarLifts, key = { option -> option.id }) { option ->
                            LiftSwapOptionRow(
                                option = option,
                                onClick = { onLiftSelected(option.id) },
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { allLiftsExpanded = !allLiftsExpanded }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(rotation),
                                )
                            }
                        }

                        if (allLiftsExpanded) {
                            items(remainingLifts, key = { option -> option.id }) { option ->
                                LiftSwapOptionRow(
                                    option = option,
                                    onClick = { onLiftSelected(option.id) },
                                )
                            }
                        }
                    }
                } else {
                    items(state.options, key = { option -> option.id }) { option ->
                        LiftSwapOptionRow(
                            option = option,
                            onClick = { onLiftSelected(option.id) },
                        )
                    }
                }

                if (state.options.isEmpty()) {
                    item(key = "empty-state") {
                        StratumText(
                            text = stringResource(R.string.lift_swap_empty),
                            muted = true,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }

            StratumSearchField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
            )
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


private const val SIMILARITY_THRESHOLD = 0.4f
