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
import com.stratum.core.ui.component.LiftLibraryFilter
import com.stratum.core.ui.component.LiftSwapDropdownOption
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapMuscleTiers
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.core.ui.component.StratumDropdownMenu
import com.stratum.core.ui.component.StratumDropdownMenuItem
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.StratumMuscleFilterRow
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
    liftId: String,
    liftName: String,
    onBackClick: () -> Unit,
    onSwapSelected: (newLiftId: String) -> Unit,
    onCreateNewLift: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiftSwapViewModel = hiltViewModel(),
) {
    LaunchedEffect(liftId, liftName) {
        viewModel.initialize(liftId, liftName)
    }

    val state by viewModel.liftSwapState.collectAsStateWithLifecycle()

    state?.let { activeState ->
        LiftSwapScreen(
            state = activeState,
            onLiftSelected = onSwapSelected,
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
    val isDefaultScreen = !state.hasActiveFilter && state.searchQuery.isBlank()

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.lift_swap_title, state.targetLiftName),
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
            )

            StratumMuscleFilterRow(
                upperCards = state.muscleGroups.upper,
                lowerCards = state.muscleGroups.lower,
                selectedMuscles = state.selectedMuscles,
                onMuscleToggled = { muscle -> onFilterIntent(LiftSwapFilterIntent.ToggleMuscle(muscle)) },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isDefaultScreen) {
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
}


@Composable
private fun FilterDropdownRow(
    state: LiftSwapSheetState,
    onFilterIntent: (LiftSwapFilterIntent) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val listState = rememberLazyListState()

    val selectedIndex = state.activeFilterChipKey?.let { key ->
        FILTER_CHIP_ORDER.indexOf(key).takeIf { it >= 0 }
    }
    LaunchedEffect(selectedIndex) {
        selectedIndex?.let { listState.animateScrollToItem(it) }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.hasActiveFilter) {
            StratumIconButton(
                onClick = { onFilterIntent(LiftSwapFilterIntent.ResetAll) },
                containerColor = colors.surfaceInset,
                contentColor = colors.textMuted,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_filter_reset),
                    contentDescription = stringResource(R.string.lift_swap_reset_filters_content_description),
                    tint = colors.textMuted,
                )
            }
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "bias") {
                FilterDropdownChip(
                    label = stringResource(R.string.lift_swap_filter_bias),
                    leadingIconRes = R.drawable.ic_bias,
                    options = state.biasOptions,
                    selectedValue = state.selectedBias,
                    onSelect = { bias: MuscleLengthBias? -> onFilterIntent(LiftSwapFilterIntent.SelectBias(bias)) },
                )
            }
            item(key = "lift-type") {
                FilterDropdownChip(
                    label = stringResource(R.string.lift_swap_filter_lift_type),
                    leadingIconRes = R.drawable.ic_lift_type,
                    options = state.categoryOptions,
                    selectedValue = state.selectedCategory,
                    onSelect = { category: LiftCategory? -> onFilterIntent(LiftSwapFilterIntent.SelectCategory(category)) },
                )
            }
            item(key = "library") {
                FilterDropdownChip(
                    label = stringResource(R.string.lift_swap_filter_library),
                    leadingIconRes = R.drawable.ic_library_books,
                    options = state.libraryOptions,
                    selectedValue = state.selectedLibrary,
                    onSelect = { library: LiftLibraryFilter? -> onFilterIntent(LiftSwapFilterIntent.SelectLibrary(library)) },
                )
            }
        }
    }
}

@Composable
private fun <T> FilterDropdownChip(
    label: String,
    @androidx.annotation.DrawableRes leadingIconRes: Int,
    options: List<LiftSwapDropdownOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val hasSelection = selectedValue != null
    val borderColor = if (hasSelection) colors.accent else colors.borderSubtle
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier =
                Modifier
                    .heightIn(min = MIN_TOUCH_HEIGHT)
                    .background(color = colors.surfaceRaised, shape = shapes.chip)
                    .border(width = shapes.borderWidth, color = borderColor, shape = shapes.chip)
                    .clickable { expanded = true }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                tint = if (hasSelection) colors.accent else colors.textMuted,
            )
            StratumText(
                text = label,
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = !hasSelection,
            )
            if (hasSelection) {
                StratumText(
                    text = ": ${options.firstOrNull { it.value == selectedValue }?.label.orEmpty()}",
                    style = StratumThemeAccessor.typography.bodyLarge,
                )
            }
        }
        StratumDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                StratumDropdownMenuItem(
                    text = option.label,
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                )
            }
        }
    }
}

@Composable
internal fun LiftSwapOptionRow(
    option: LiftSwapOptionState,
    onClick: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiftThumbnail(name = option.name, imageUrl = option.imageUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StratumText(
                text = option.name,
                style = StratumThemeAccessor.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (option.categoryLabel.isNotBlank()) {
                    MetadataBadge(text = option.categoryLabel)
                }
                option.primaryMuscleLabels.forEach { muscle ->
                    if (muscle.isNotBlank()) {
                        MetadataBadge(text = muscle)
                    }
                }
                if (option.lengthBiasLabel.isNotBlank()) {
                    MetadataBadge(text = option.lengthBiasLabel)
                }
            }
        }
        if (option.isCustom) {
            LibraryBadge()
        }
    }
}

@Composable
private fun LiftThumbnail(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val initial = remember(name) {
        name.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()
    }

    Box(
        modifier =
            modifier
                .size(THUMBNAIL_SIZE)
                .background(color = colors.surfaceSunken, shape = shapes.chip)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.chip),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(
            text = initial,
            style = StratumThemeAccessor.typography.titleLarge,
            muted = true,
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier.size(THUMBNAIL_SIZE).clip(shapes.chip),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun MetadataBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Box(
        modifier =
            modifier
                .background(color = colors.surfaceInset, shape = shapes.chip)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        StratumText(
            text = text,
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
    }
}

@Composable
private fun LibraryBadge() {
    MetadataBadge(text = stringResource(R.string.lift_swap_library_custom))
}

private val MIN_TOUCH_HEIGHT = 48.dp
private val THUMBNAIL_SIZE = 48.dp
private const val SIMILARITY_THRESHOLD = 0.4f

/** Emission order of the dropdown chips in [FilterDropdownRow], for auto-scroll indexing. */
private val FILTER_CHIP_ORDER = listOf("bias", "lift-type", "library")
