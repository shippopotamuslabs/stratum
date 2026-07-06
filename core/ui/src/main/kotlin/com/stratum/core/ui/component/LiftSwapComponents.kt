package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        muscleGroups.upper.forEach { card ->
                            StratumMuscleFilterCard(
                                state = card,
                                selected = card.muscle in selectedMuscles,
                                onClick = { onMuscleToggled(card.muscle) },
                            )
                        }
                    }
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        muscleGroups.lower.forEach { card ->
                            StratumMuscleFilterCard(
                                state = card,
                                selected = card.muscle in selectedMuscles,
                                onClick = { onMuscleToggled(card.muscle) },
                            )
                        }
                    }
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
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            modifier
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

private val MIN_TOUCH_HEIGHT = 48.dp
private val THUMBNAIL_SIZE = 48.dp
private val FILTER_CHIP_ORDER = listOf("muscles", "bias", "lift-type", "library")
