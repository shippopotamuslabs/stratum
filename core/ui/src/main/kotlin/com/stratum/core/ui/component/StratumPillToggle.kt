package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumColors
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Single-select toggle for low-emphasis filters (e.g. Upper/Lower, Primary/Secondary).
 * Options render as plain text; the active option sits on a borderless ovular pill fill.
 * Segment min height is 48.dp (UI_DESIGN.md touch-target guidance for tappable chrome).
 */
@Composable
fun StratumPillToggle(
    segments: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedColor: Color = StratumThemeAccessor.colors.surfaceRaised,
) {
    require(segments.isNotEmpty()) { "StratumPillToggle requires at least one segment." }
    require(selectedIndex in segments.indices) {
        "selectedIndex $selectedIndex is out of bounds for ${segments.size} segments."
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PillToggleSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            PillToggleSegment(
                label = label,
                selected = selected,
                enabled = enabled,
                onClick = { onSelectedIndexChange(index) },
                contentDescription = label + if (selected) ", selected" else "",
                selectedColor = selectedColor,
            )
        }
    }
}

@Composable
private fun PillToggleSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    selectedColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val pillShape = RoundedCornerShape(percent = 50)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .heightIn(min = MinPillToggleHeight)
                .clip(pillShape)
                .background(pillBackground(selected = selected, enabled = enabled, selectedColor = selectedColor))
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                    this.contentDescription = contentDescription
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = PillHorizontalPadding, vertical = PillVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                StratumThemeAccessor.typography.sectionLabel.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color = pillLabelColor(selected = selected, enabled = enabled, colors = colors),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun pillBackground(
    selected: Boolean,
    enabled: Boolean,
    selectedColor: Color,
): Color =
    when {
        !enabled -> Color.Transparent
        // Raised above surfaceBase — surfaceSunken is darker than the screen and reads as a black hole here.
        selected -> selectedColor
        else -> Color.Transparent
    }

private fun pillLabelColor(
    selected: Boolean,
    enabled: Boolean,
    colors: StratumColors,
): Color =
    when {
        !enabled -> colors.textMuted
        selected -> colors.textPrimary
        else -> colors.textMuted
    }

private val PillHorizontalPadding = 14.dp
private val PillVerticalPadding = 10.dp
private val PillToggleSpacing = 4.dp
/** Matches UI_DESIGN.md §5 minimum touch target (48dp). */
private val MinPillToggleHeight = 48.dp
