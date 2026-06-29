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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                .width(220.dp)
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (regions.isNotEmpty()) {
            BodyHighlightCanvas(
                regions = regions,
                highlightMuscle = state.muscle,
                modifier = Modifier.size(width = 72.dp, height = 96.dp),
            )
        } else {
            BoxPlaceholder(modifier = Modifier.size(width = 72.dp, height = 96.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = state.displayName,
                style = StratumThemeAccessor.typography.titleLarge,
                color = StratumThemeAccessor.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StratumText(
                text = exerciseCountLabel(state.exerciseCount),
                muted = true,
            )
            StratumNumber(text = setCountLabel(state.setCount))
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

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            StratumSegmentedControl(
                modifier = Modifier.fillMaxWidth(VOLUME_TIER_TOGGLE_WIDTH_FRACTION),
                segments = volumeTierLabels,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index -> selectedIndex = index },
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

private const val VOLUME_TIER_TOGGLE_WIDTH_FRACTION = 0.5f

private val volumeTierLabels = listOf("Upper", "Lower")
