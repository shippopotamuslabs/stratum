package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact status pill for “Active” (or similar). When [active] is true, fills with accent.
 * Optional [onClick] makes inactive pills tappable to promote activation.
 */
@Composable
fun StratumActiveBadge(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val clickable =
        if (onClick != null && !active) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .clip(shapes.chip)
                .then(clickable)
                .background(
                    color = if (active) colors.accent else colors.surfaceInset,
                    shape = shapes.chip,
                )
                .border(
                    width = shapes.borderWidth,
                    color = if (active) colors.accent else colors.borderSubtle,
                    shape = shapes.chip,
                )
                .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
    ) {
        Text(
            text = label,
            style = StratumThemeAccessor.typography.sectionLabel,
            color = if (active) colors.onAccent else colors.textMuted,
        )
    }
}
