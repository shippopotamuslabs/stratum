package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.component.musclepicker.BodyHighlightCanvas
import com.stratum.core.ui.component.musclepicker.bodyRegionsForHighlight
import com.stratum.core.ui.theme.StratumThemeAccessor
import java.util.Locale

/**
 * Single-muscle volume summary card: anatomical highlight, muscle name, exercise count, set count.
 */
@Composable
fun StratumMuscleVolumeCard(
    state: StratumMuscleVolumeCardState,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val regions = bodyRegionsForHighlight(state.muscle)

    Row(
        modifier =
            modifier
                .width(VOLUME_CARD_WIDTH)
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .padding(VOLUME_CARD_PADDING),
        horizontalArrangement = Arrangement.spacedBy(VOLUME_CARD_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (regions.isNotEmpty()) {
            BodyHighlightCanvas(
                regions = regions,
                highlightMuscle = state.muscle,
                modifier = Modifier.size(width = VOLUME_ANATOMY_WIDTH, height = VOLUME_ANATOMY_HEIGHT),
            )
        } else {
            BoxPlaceholder(modifier = Modifier.size(width = VOLUME_ANATOMY_WIDTH, height = VOLUME_ANATOMY_HEIGHT))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(VOLUME_TEXT_SPACING),
        ) {
            StratumText(
                text = state.displayName,
                style = volumeCardTitleStyle(),
                maxLines = 1,
            )
            StratumText(
                text = exerciseCountLabel(state.exerciseCount),
                style = volumeCardMetaStyle(),
                muted = true,
                maxLines = 1,
            )
            StratumNumber(
                text = setCountLabel(state.setCount),
                style = volumeCardDataStyle(),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun StratumMuscleVolumeRow(
    upperCards: List<StratumMuscleVolumeCardState>,
    lowerCards: List<StratumMuscleVolumeCardState>,
    modifier: Modifier = Modifier,
    title: String = "Volume",
    initialTier: StratumVolumeBodyTier = StratumVolumeBodyTier.Upper,
) {
    var selectedIndex by remember(initialTier, upperCards, lowerCards) {
        mutableIntStateOf(
            when {
                upperCards.isEmpty() && lowerCards.isNotEmpty() -> LOWER_TIER_INDEX
                initialTier == StratumVolumeBodyTier.Lower && lowerCards.isNotEmpty() -> LOWER_TIER_INDEX
                else -> UPPER_TIER_INDEX
            },
        )
    }
    val showTierToggle = upperCards.isNotEmpty() && lowerCards.isNotEmpty()
    val visibleCards =
        when {
            showTierToggle && selectedIndex == LOWER_TIER_INDEX -> lowerCards
            showTierToggle -> upperCards
            lowerCards.isNotEmpty() -> lowerCards
            else -> upperCards
        }

    StratumMuscleVolumeRowContent(
        title = title,
        toggleLabels = if (showTierToggle) volumeTierLabels else null,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index -> selectedIndex = index },
        visibleCards = visibleCards,
        modifier = modifier,
    )
}

@Composable
fun StratumScopedMuscleVolumeRow(
    workoutCards: List<StratumMuscleVolumeCardState>,
    programCards: List<StratumMuscleVolumeCardState>,
    modifier: Modifier = Modifier,
    title: String = "Volume",
    scopeLabels: List<String> = volumeScopeLabels,
    initialScope: StratumVolumeScope = StratumVolumeScope.Workout,
) {
    var selectedIndex by remember(initialScope, workoutCards, programCards) {
        mutableIntStateOf(
            when {
                workoutCards.isEmpty() && programCards.isNotEmpty() -> PROGRAM_SCOPE_INDEX
                initialScope == StratumVolumeScope.Program && programCards.isNotEmpty() -> PROGRAM_SCOPE_INDEX
                else -> WORKOUT_SCOPE_INDEX
            },
        )
    }
    val showScopeToggle = workoutCards.isNotEmpty() && programCards.isNotEmpty()
    val visibleCards =
        when {
            showScopeToggle && selectedIndex == PROGRAM_SCOPE_INDEX -> programCards
            showScopeToggle -> workoutCards
            programCards.isNotEmpty() -> programCards
            else -> workoutCards
        }

    StratumMuscleVolumeRowContent(
        title = title,
        toggleLabels = if (showScopeToggle) scopeLabels else null,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index -> selectedIndex = index },
        visibleCards = visibleCards,
        modifier = modifier,
    )
}

@Composable
private fun StratumMuscleVolumeRowContent(
    title: String,
    toggleLabels: List<String>?,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    visibleCards: List<StratumMuscleVolumeCardState>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VOLUME_ROW_SPACING)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = title,
                style = StratumThemeAccessor.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (toggleLabels != null) {
                StratumPillToggle(
                    segments = toggleLabels,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = onSelectedIndexChange,
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VOLUME_CARD_SPACING)) {
            items(visibleCards, key = { card -> card.muscle.name }) { card ->
                StratumMuscleVolumeCard(state = card)
            }
        }
    }
}

@Composable
private fun BoxPlaceholder(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier =
            modifier
                .background(
                    color = StratumThemeAccessor.colors.surfaceSunken,
                    shape = StratumThemeAccessor.shapes.panel,
                ),
    )
}

private fun exerciseCountLabel(count: Int): String =
    if (count == 1) {
        "1 exercise"
    } else {
        "$count exercises"
    }

private fun setCountLabel(setCount: Double): String {
    val formatted = String.format(Locale.US, "%.1f", setCount)
    return if (setCount == 1.0) {
        "1 set"
    } else {
        "$formatted sets"
    }
}

private const val UPPER_TIER_INDEX = 0
private const val LOWER_TIER_INDEX = 1
private const val WORKOUT_SCOPE_INDEX = 0
private const val PROGRAM_SCOPE_INDEX = 1

private val volumeTierLabels = listOf("Upper", "Lower")
private val volumeScopeLabels = listOf("Workout", "Program")

private val VOLUME_CARD_WIDTH = 125.dp
private val VOLUME_CARD_PADDING = 6.dp
private val VOLUME_CARD_SPACING = 6.dp
private val VOLUME_ANATOMY_WIDTH = 36.dp
private val VOLUME_ANATOMY_HEIGHT = 48.dp
private val VOLUME_TEXT_SPACING = 2.dp
private val VOLUME_ROW_SPACING = 6.dp

@Composable
private fun volumeCardTitleStyle() =
    StratumThemeAccessor.typography.sectionLabel.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
    )

@Composable
private fun volumeCardMetaStyle() =
    StratumThemeAccessor.typography.sectionLabel.copy(
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

@Composable
private fun volumeCardDataStyle() =
    StratumThemeAccessor.typography.sectionLabel.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )
