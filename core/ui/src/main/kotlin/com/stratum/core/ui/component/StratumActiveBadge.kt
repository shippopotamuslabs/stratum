package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact status pill for “Active” (or similar). When [active] is true, fills with accent.
 * Optional [onClick] makes inactive pills tappable to promote activation.
 *
 * §12.5: the tappable variant follows the StratumChip split — a real 48dp
 * layout target outside, the ~26dp pill as the visual inside it.
 */
@Composable
fun StratumActiveBadge(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (onClick == null || active) {
        BadgePill(
            label = label,
            active = active,
            modifier = modifier,
        )
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        BadgePill(
            label = label,
            active = active,
            interactionSource = interactionSource,
        )
    }
}

@Composable
private fun BadgePill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    var pillModifier =
        modifier
            .clip(shapes.chip)
            .background(
                color = if (active) colors.accent else colors.surfaceInset,
                shape = shapes.chip,
            )
            .border(
                width = shapes.borderWidth,
                color = if (active) colors.accent else colors.borderSubtle,
                shape = shapes.chip,
            )
    if (interactionSource != null) {
        pillModifier = pillModifier.indication(interactionSource, ripple())
    }
    Box(
        modifier = pillModifier.padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
    ) {
        Text(
            text = label,
            style = StratumThemeAccessor.typography.sectionLabel,
            color = if (active) colors.onAccent else colors.textMuted,
        )
    }
}
