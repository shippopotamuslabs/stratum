package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 *
 * The card takes its width from the grid (which passes a computed `Modifier.width`), and
 * always reserves the anatomy canvas area, so muscles without a body diagram (e.g.
 * Abductors/Adductors) match the height of the others with the label centered below.
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
        } else {
            // Reserve the same anatomy slot for muscles without a body diagram so every card
            // has identical height and the label sits centered below the empty space.
            Box(modifier = Modifier.size(width = ANATOMY_WIDTH, height = ANATOMY_HEIGHT))
        }
        StratumText(
            text = state.displayName,
            style = filterCardTitleStyle(),
            maxLines = 1,
            muted = !selected,
        )
    }
}

@Composable
private fun filterCardTitleStyle() =
    StratumThemeAccessor.typography.sectionLabel.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

private val FILTER_CARD_PADDING = 6.dp
private val FILTER_CARD_SPACING = 6.dp
private val ANATOMY_WIDTH = 36.dp
private val ANATOMY_HEIGHT = 48.dp
