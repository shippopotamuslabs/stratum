package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

private val ChipMinHeight = 32.dp
private val AvatarSize = 24.dp

/**
 * The unified non-filter chip (§4.1, §12.3): Input, Assist and Suggestion share
 * this one implementation. Visual height 32dp, label labelLarge. Selected reads
 * tonal (accentMuted container, onAccentMuted content), never the bright accent.
 *
 * - Interactive (Input/Assist): pass [onClick]. The touch target is 48dp via
 *   minimumInteractiveComponentSize while the visual chip stays 32dp (§12.5,
 *   the same split [StratumButton] gets from M3).
 * - Suggestion: leave [onClick] null — the chip renders quiet (secondary label,
 *   no click target), per §12.3's non-interactive-looking rule.
 * - Input chips may carry a 24dp circular [avatar] leading slot; icon-led chips
 *   use [leadingIcon] (18dp content by convention). Icon slots inherit the
 *   resolved label color as their content color.
 *
 * Selectable filter semantics live in [StratumFilterChip]; this chip is a plain
 * action/suggestion pill.
 */
@Composable
fun StratumChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = StratumThemeAccessor.colors
    val container =
        when {
            !enabled -> colors.surfaceSunken
            selected -> colors.accentMuted
            else -> colors.surfaceRaised
        }
    val resolvedLabelColor =
        when {
            !enabled -> colors.textMuted
            selected -> colors.onAccentMuted
            onClick == null -> colors.textMuted
            else -> colors.textPrimary
        }
    if (onClick == null) {
        StratumChipVisual(
            label = label,
            container = container,
            labelColor = resolvedLabelColor,
            leadingIcon = leadingIcon,
            avatar = avatar,
            trailingIcon = trailingIcon,
            modifier = modifier,
        )
    } else {
        // The clickable lives on the 48dp outer box so the whole reserved
        // target responds; the ripple is drawn clipped to the 32dp visual via
        // the shared interaction source.
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier =
                modifier
                    .minimumInteractiveComponentSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            StratumChipVisual(
                label = label,
                container = container,
                labelColor = resolvedLabelColor,
                leadingIcon = leadingIcon,
                avatar = avatar,
                trailingIcon = trailingIcon,
                interactionSource = interactionSource,
            )
        }
    }
}

@Composable
private fun StratumChipVisual(
    label: String,
    container: Color,
    labelColor: Color,
    leadingIcon: (@Composable () -> Unit)?,
    avatar: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    var visualModifier =
        modifier
            .heightIn(min = ChipMinHeight)
            .clip(shapes.chip)
            .background(color = container, shape = shapes.chip)
            .border(
                width = shapes.borderWidth,
                color = colors.borderSubtle,
                shape = shapes.chip,
            )
    if (interactionSource != null) {
        visualModifier = visualModifier.indication(interactionSource, ripple())
    }
    Row(
        modifier =
            visualModifier.padding(
                start = if (avatar != null) StratumSpacing.s8 else StratumSpacing.s16,
                end = StratumSpacing.s16,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        if (avatar != null) {
            Box(
                modifier =
                    Modifier
                        .size(AvatarSize)
                        .clip(CircleShape),
            ) {
                avatar()
            }
        } else if (leadingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides labelColor) {
                leadingIcon()
            }
        }
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.labelLarge,
            color = labelColor,
        )
        if (trailingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides labelColor) {
                trailingIcon()
            }
        }
    }
}
