package com.stratum.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.LiftCategory
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.core.ui.component.toVolumeDisplayName

@Composable
fun FilterDropdownRow(
    state: LiftSwapSheetState,
    onFilterIntent: (LiftSwapFilterIntent) -> Unit,
    onMuscleFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
            item(key = "muscles") {
                MuscleFilterChip(
                    selectedCount = state.selectedMuscles.size,
                    onClick = onMuscleFilterClick,
                )
            }
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
private fun MuscleFilterChip(
    selectedCount: Int,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val hasSelection = selectedCount > 0
    val borderColor = if (hasSelection) colors.accent else colors.borderSubtle

    Row(
        modifier =
            Modifier
                .heightIn(min = MIN_TOUCH_HEIGHT)
                .background(color = colors.surfaceRaised, shape = shapes.chip)
                .border(width = shapes.borderWidth, color = borderColor, shape = shapes.chip)
                .clickable { onClick() }
                .semantics { role = Role.Button }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_accessibility),
            contentDescription = null,
            tint = if (hasSelection) colors.accent else colors.textMuted,
        )
        StratumText(
            text = if (hasSelection) {
                "${stringResource(R.string.lift_swap_filter_muscles)} ($selectedCount)"
            } else {
                stringResource(R.string.lift_swap_filter_muscles)
            },
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = !hasSelection,
        )
    }
}

@Composable
fun SelectedMusclesRow(
    selectedMuscles: Set<MuscleGroup>,
    onMuscleToggled: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedMuscles.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(selectedMuscles.toList(), key = { it.name }) { muscle ->
            StratumFilterChip(
                label = muscle.toVolumeDisplayName(),
                selected = true,
                onSelectedChange = { onMuscleToggled(muscle) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Remove ${muscle.toVolumeDisplayName()}",
                        tint = StratumThemeAccessor.colors.onAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleFilterBottomSheet(
    onDismissRequest: () -> Unit,
    selectedMuscles: Set<MuscleGroup>,
    muscleGroups: LiftSwapMuscleTiers,
    onMuscleToggled: (MuscleGroup) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.lift_swap_filter_muscle_groups),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "upper-body-header") {
                    StratumText(
                        text = "Upper Body",
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                item(key = "upper-body-grid") {
                    MuscleFilterGrid(
                        cards = muscleGroups.upper,
                        selectedMuscles = selectedMuscles,
                        onMuscleToggled = onMuscleToggled,
                    )
                }
                item(key = "lower-body-header") {
                    StratumText(
                        text = "Lower Body",
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                item(key = "lower-body-grid") {
                    MuscleFilterGrid(
                        cards = muscleGroups.lower,
                        selectedMuscles = selectedMuscles,
                        onMuscleToggled = onMuscleToggled,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedMuscles.isNotEmpty()) {
                    StratumOutlinedButton(
                        text = "Clear All",
                        onClick = onClearAll,
                        modifier = Modifier.weight(1f),
                    )
                }
                StratumButton(
                    text = "Done",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Grid of [StratumMuscleFilterCard]s that fills the available row width. Measures the
 * available width via [BoxWithConstraints], computes how many cards fit per row at the
 * minimum card width, then gives every card a fixed computed width so all cards share the
 * same size and a trailing partial row leaves blank space on the right instead of
 * stretching. The cards-per-row count adapts to sheet width, orientation, and font scale.
 */
@Composable
private fun MuscleFilterGrid(
    cards: List<StratumMuscleFilterCardState>,
    selectedMuscles: Set<MuscleGroup>,
    onMuscleToggled: (MuscleGroup) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val gapPx = with(density) { MUSCLE_GRID_SPACING.toPx() }
        val minCardWidthPx = with(density) { MUSCLE_MIN_CARD_WIDTH.toPx() }
        val itemsPerRow = ((maxWidthPx + gapPx) / (minCardWidthPx + gapPx))
            .toInt()
            .coerceAtLeast(1)
        // Equal fixed width per card: (totalWidth - (n-1)*gap) / n. Trailing cards in a
        // partial row keep this same width so they don't stretch to fill the row.
        val cardWidth = with(density) {
            ((maxWidthPx - (itemsPerRow - 1) * gapPx) / itemsPerRow).toDp()
        }

        Column(verticalArrangement = Arrangement.spacedBy(MUSCLE_GRID_SPACING)) {
            cards.chunked(itemsPerRow).forEach { rowCards ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MUSCLE_GRID_SPACING),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowCards.forEach { card ->
                        StratumMuscleFilterCard(
                            state = card,
                            selected = card.muscle in selectedMuscles,
                            onClick = { onMuscleToggled(card.muscle) },
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }
        }
    }
}

private val MUSCLE_GRID_SPACING = 8.dp
private val MUSCLE_MIN_CARD_WIDTH = 100.dp

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiftSwapOptionRow(
    option: LiftSwapOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val borderColor = if (selected) colors.accent else colors.borderSubtle
    val borderWidth = if (selected) shapes.borderWidth * 2 else shapes.borderWidth
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = borderWidth, color = borderColor, shape = shapes.panel)
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
        if (trailingContent != null) {
            trailingContent()
        } else if (option.isCustom) {
            LibraryBadge()
        }
    }
}

@Composable
fun LiftThumbnail(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = THUMBNAIL_SIZE,
    /** When false, image/initials fill a borderless clipped square (e.g. workout navigator tabs). */
    bordered: Boolean = true,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val initial = remember(name) {
        name.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()
    }

    Box(
        modifier =
            modifier
                .size(size)
                .background(color = colors.surfaceSunken, shape = shapes.chip)
                .then(
                    if (bordered) {
                        Modifier.border(
                            width = shapes.borderWidth,
                            color = colors.borderSubtle,
                            shape = shapes.chip,
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(shapes.chip),
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
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun MetadataBadge(
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
fun LibraryBadge() {
    MetadataBadge(text = stringResource(R.string.lift_swap_library_custom))
}

/**
 * Compact tray of selected lift thumbnails for the lift-picker top bar. Shows up to
 * [VISIBLE_SELECTED_THUMBNAILS] images plus a `+N` overflow badge, with a trailing chevron
 * that expands/collapses the full selected-lifts panel.
 *
 * Designed for the top-app-bar title slot ([compact] density) so appearing does not push
 * scaffold content down.
 */
@Composable
fun SelectedLiftsTray(
    selectedOptions: List<LiftSwapOptionState>,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedOptions.isEmpty()) return

    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val visible = selectedOptions.take(VISIBLE_SELECTED_THUMBNAILS)
    val overflow = selectedOptions.size - visible.size
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "selectedLiftsChevron",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable(onClick = onExpandToggle)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visible.forEach { option ->
                LiftThumbnail(
                    name = option.name,
                    imageUrl = option.imageUrl,
                    size = TRAY_THUMBNAIL_SIZE,
                )
            }
            if (overflow > 0) {
                Box(
                    modifier =
                        Modifier
                            .size(TRAY_THUMBNAIL_SIZE)
                            .background(color = colors.surfaceSunken, shape = shapes.chip)
                            .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.chip),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(
                        text = stringResource(R.string.lift_swap_overflow_count, overflow),
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                    )
                }
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription =
                stringResource(
                    if (expanded) {
                        R.string.lift_swap_collapse_selected_content_description
                    } else {
                        R.string.lift_swap_expand_selected_content_description
                    },
                ),
            tint = colors.textPrimary,
            modifier =
                Modifier
                    .size(20.dp)
                    .rotate(rotation),
        )
    }
}

/**
 * Bottom search chrome shared by lift multi-select flows (add-lift swap, gym merge).
 * Optional trailing [doneLabel] button when [showDone] is true.
 */
@Composable
fun LiftPickerSearchBottomBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    showDone: Boolean = false,
    doneEnabled: Boolean = false,
    doneLabel: String? = null,
    onDoneClick: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.weight(1f),
            placeholder = stringResource(R.string.lift_swap_search_placeholder),
        )
        if (showDone) {
            StratumButton(
                text = doneLabel ?: stringResource(R.string.lift_swap_done),
                enabled = doneEnabled,
                onClick = onDoneClick,
            )
        }
    }
}

/**
 * Expandable multi-select panel: optional [header] (e.g. superset toggle) + selected lift rows
 * with remove affordance. Shared by Lift Swap add-mode and Gym merge mode.
 */
@Composable
fun SelectedLiftsListPanel(
    selectedOptions: List<LiftSwapOptionState>,
    onDeselectLift: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        header?.invoke()

        StratumText(
            text = stringResource(R.string.lift_swap_selected_lifts_title),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .then(Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(selectedOptions, key = { option -> option.id }) { option ->
                LiftSwapOptionRow(
                    option = option,
                    selected = true,
                    onClick = { onDeselectLift(option.id) },
                    trailingContent = {
                        StratumIconButton(
                            onClick = { onDeselectLift(option.id) },
                            containerColor = Color.Transparent,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription =
                                    stringResource(
                                        R.string.lift_swap_remove_selected_content_description,
                                        option.name,
                                    ),
                                tint = StratumThemeAccessor.colors.textMuted,
                            )
                        }
                    },
                )
            }
        }
    }
}

private val MIN_TOUCH_HEIGHT = 48.dp
private val THUMBNAIL_SIZE = 48.dp
/** Sized to fit the standard top-app-bar title row without expanding scaffold insets. */
private val TRAY_THUMBNAIL_SIZE = 32.dp
private const val VISIBLE_SELECTED_THUMBNAILS = 6
private val FILTER_CHIP_ORDER = listOf("muscles", "bias", "lift-type", "library")
