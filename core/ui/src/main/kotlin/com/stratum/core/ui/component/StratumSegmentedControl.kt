package com.stratum.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumColors
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Single-select segmented control. Segments sit flush with a shared outer border; only the
 * outer corners are rounded. The active segment fills with [StratumThemeAccessor.colors.accent].
 */
@Composable
fun StratumSegmentedControl(
    modifier: Modifier = Modifier,
    segments: List<String>,
    selectedIndex: Int?,
    onSelectedIndexChange: (Int) -> Unit,
    enabled: Boolean = true,
    leadingIconRes: List<Int?> = emptyList(),
    /** When true at an index, a small dirty-dot trails that segment's label. */
    modified: List<Boolean> = emptyList(),
) {
    require(segments.isNotEmpty()) { "StratumSegmentedControl requires at least one segment." }
    if (selectedIndex != null) {
        require(selectedIndex in segments.indices) {
            "selectedIndex $selectedIndex is out of bounds for ${segments.size} segments."
        }
    }

    ConnectedSegmentBar(
        modifier = modifier,
    ) { segmentModifier ->
        segments.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val isModified = modified.getOrNull(index) == true
            ConnectedSegmentCell(
                label = label,
                selected = selected,
                enabled = enabled,
                onClick = { onSelectedIndexChange(index) },
                modifier = segmentModifier(index),
                leadingIconRes = leadingIconRes.getOrNull(index),
                modified = isModified,
            )
        }
    }
}

/**
 * Multi-select variant of the connected segment bar. Each segment toggles independently while
 * keeping the shared outer border and inner dividers.
 */
@Composable
fun StratumToggleGroup(
    options: List<String>,
    selectedOptions: Set<String>,
    onOptionToggle: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    require(options.isNotEmpty()) { "StratumToggleGroup requires at least one option." }

    ConnectedSegmentBar(
        modifier = modifier,
    ) { segmentModifier ->
        options.forEachIndexed { index, label ->
            val selected = label in selectedOptions
            ConnectedSegmentCell(
                label = label,
                selected = selected,
                enabled = enabled,
                onClick = { onOptionToggle(label) },
                modifier = segmentModifier(index),
            )
        }
    }
}

@Composable
private fun ConnectedSegmentBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(segmentModifier: (Int) -> Modifier) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val outerShape = RoundedCornerShape(shapes.chipCorner)

    val elevations = StratumThemeAccessor.elevations
    val trackBorder =
        if (elevations.useHardShadow) colors.borderStrong else colors.borderSubtle
    Box(
        modifier = modifier
            .height(MinSegmentHeight)
            .border(shapes.borderWidth, trackBorder, outerShape)
            .clip(outerShape),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            content { index ->
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(
                        if (index > 0) {
                            Modifier.drawBehind {
                                drawLine(
                                    color = colors.borderSubtle,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = shapes.borderWidth.toPx(),
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
            }
        }
    }
}

@Composable
private fun ConnectedSegmentCell(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIconRes: Int? = null,
    modified: Boolean = false,
) {
    val colors = StratumThemeAccessor.colors
    val background = segmentBackground(selected = selected, enabled = enabled, colors = colors)
    val labelColor = when {
        !enabled -> colors.textMuted
        selected -> colors.onAccent
        else -> colors.textPrimary
    }
    val modifiedLabel = stringResource(R.string.stratum_segment_modified)
    val tabDescription =
        buildString {
            append(label)
            if (selected) append(", selected")
            if (modified) {
                append(", ")
                append(modifiedLabel)
            }
        }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .background(background)
            .semantics {
                role = Role.Tab
                this.selected = selected
                this.contentDescription = tabDescription
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            if (leadingIconRes != null) {
                Icon(
                    painter = painterResource(leadingIconRes),
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = StratumThemeAccessor.typography.bodyLarge,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (modified) {
                Box(
                    modifier =
                        Modifier
                            .size(ModifiedDotSize)
                            .clip(CircleShape)
                            .background(
                                if (selected) colors.onAccent else colors.accent,
                            ),
                )
            }
        }
    }
}

private fun segmentBackground(
    selected: Boolean,
    enabled: Boolean,
    colors: StratumColors,
): Color = when {
    !enabled -> colors.surfaceSunken
    selected -> colors.accent
    else -> colors.surfaceRaised
}

private val MinSegmentHeight = 48.dp
private val ModifiedDotSize = 6.dp
