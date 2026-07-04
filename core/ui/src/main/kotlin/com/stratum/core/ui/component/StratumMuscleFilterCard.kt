package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.component.musclepicker.BodyHighlightCanvas
import com.stratum.core.ui.component.musclepicker.bodyRegionsForHighlight
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Selectable muscle card for the swap sheet's muscle-group filter. Rendered as the anatomy
 * highlight with the muscle **name below** (UI_DESIGN.md §7.1 — matches the ProgramsScreen
 * volume carousel visual language, minus the volume counts). Selection toggles a stronger
 * border + accent-filled anatomy; tap toggles membership in the filter set.
 */
@Composable
fun StratumMuscleFilterCard(
    state: StratumMuscleFilterCardState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val regions = bodyRegionsForHighlight(state.muscle)
    val borderColor = if (selected) colors.accent else colors.borderSubtle
    val borderWidth = if (selected) shapes.borderWidth * 2 else shapes.borderWidth

    Column(
        modifier =
            modifier
                .width(FILTER_CARD_WIDTH)
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = borderWidth, color = borderColor, shape = shapes.panel)
                .clickable(onClick = onClick)
                .semantics { role = Role.Tab }
                .padding(FILTER_CARD_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FILTER_CARD_SPACING),
    ) {
        if (regions.isNotEmpty()) {
            BodyHighlightCanvas(
                regions = regions,
                highlightMuscle = state.muscle,
                modifier = Modifier.size(width = ANATOMY_WIDTH, height = ANATOMY_HEIGHT),
            )
        }
        StratumText(
            text = state.displayName,
            style = filterCardTitleStyle(),
            maxLines = 1,
            muted = !selected,
        )
    }
}

/**
 * Upper/Lower tier toggle + horizontally-scrolling row of [StratumMuscleFilterCard]s — the
 * swap sheet's muscle-group filter. Structurally a sibling of [StratumMuscleVolumeRow] minus
 * the volume metadata, so the two read as the same component family (UI_DESIGN.md §1.2).
 *
 * Multi-select: any number of [selectedMuscles] may be held at once. The toggle is a
 * vertically-stacked two-button column pinned at the carousel's start (so it never scrolls
 * away); each button fills half the card height to match the carousel items.
 */
@Composable
fun StratumMuscleFilterRow(
    upperCards: List<StratumMuscleFilterCardState>,
    lowerCards: List<StratumMuscleFilterCardState>,
    selectedMuscles: Set<MuscleGroup>,
    onMuscleToggled: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
    initialTier: StratumVolumeBodyTier = StratumVolumeBodyTier.Upper,
) {
    var selectedIndex by remember(initialTier) {
        mutableIntStateOf(
            when (initialTier) {
                StratumVolumeBodyTier.Upper -> UPPER_TIER_INDEX
                StratumVolumeBodyTier.Lower -> LOWER_TIER_INDEX
            },
        )
    }
    val visibleCards =
        when (selectedIndex) {
            LOWER_TIER_INDEX -> lowerCards
            else -> upperCards
        }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FILTER_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(FILTER_CARD_SPACING)
    ) {
        // Pinned tier toggle: one unified bordered control with two stacked cells, full height.
        VerticalTierToggle(
            labels = tierLabels,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { selectedIndex = it },
            modifier = Modifier
                .width(TIER_TOGGLE_WIDTH)
                .fillMaxHeight(),
        )
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(FILTER_CARD_SPACING),
        ) {
            items(visibleCards, key = { card -> card.muscle.name }) { card ->
                StratumMuscleFilterCard(
                    state = card,
                    selected = card.muscle in selectedMuscles,
                    onClick = { onMuscleToggled(card.muscle) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Vertical sibling of [StratumSegmentedControl]: one unified control with cells stacked top/bottom
 * inside a shared outer border, divided by a flush horizontal line. The selected cell fills with
 * accent. Heights split equally via [Column] `weight`, so the control matches the carousel row.
 */
@Composable
private fun VerticalTierToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val outerShape = RoundedCornerShape(shapes.chipCorner)

    Box(
        modifier =
            modifier
                .border(shapes.borderWidth, colors.borderSubtle, outerShape)
                .clip(outerShape),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val isTopCell = index > 0
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (isTopCell) {
                                    Modifier.drawBehind {
                                        drawLine(
                                            color = colors.borderSubtle,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            strokeWidth = shapes.borderWidth.toPx(),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .background(if (selected) colors.accent else colors.surfaceRaised)
                            .clickable { onSelectedIndexChange(index) }
                            .semantics { role = Role.Tab },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    StratumText(
                        text = label,
                        style = filterTierToggleLabelStyle(),
                        muted = !selected,
                    )
                }
            }
        }
    }
}

private val FILTER_ROW_HEIGHT = 81.dp
private val FILTER_CARD_WIDTH = 96.dp
private val FILTER_CARD_PADDING = 6.dp
private val FILTER_CARD_SPACING = 6.dp
private val ANATOMY_WIDTH = 36.dp
private val ANATOMY_HEIGHT = 48.dp
private val TIER_TOGGLE_WIDTH = 20.dp

@Composable
private fun filterCardTitleStyle() =
    StratumThemeAccessor.typography.sectionLabel.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

@Composable
private fun filterTierToggleLabelStyle() =
    StratumThemeAccessor.typography.sectionLabel.copy(
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

private const val UPPER_TIER_INDEX = 0
private const val LOWER_TIER_INDEX = 1

private val tierLabels = listOf("U", "L")
