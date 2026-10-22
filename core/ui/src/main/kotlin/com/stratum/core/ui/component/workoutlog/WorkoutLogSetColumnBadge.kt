package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/** Shared size for set-type badges and the add-set + control so the Set column stays aligned. */
internal val WorkoutLogSetColumnBadgeSize = 32.dp

/**
 * Circular set-column badge matching template Edit Workout. Used for set-type letters and the
 * add-set `+` so both share the same left edge and visual center under the "Set" header.
 *
 * §12.5: when clickable, the badge follows the StratumChip split — a real
 * 48dp layout target on the outer box, the 32dp circle as the visual inside
 * it, the ripple drawn clipped to the circle via the shared interaction
 * source.
 */
@Composable
internal fun WorkoutLogSetColumnBadge(
    modifier: Modifier = Modifier,
    backgroundColor: Color = StratumThemeAccessor.colors.surfaceInset,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        BadgeVisual(
            modifier = modifier,
            backgroundColor = backgroundColor,
            content = content,
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
        BadgeVisual(
            backgroundColor = backgroundColor,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
private fun BadgeVisual(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    var visualModifier =
        modifier
            .size(WorkoutLogSetColumnBadgeSize)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = StratumThemeAccessor.colors.borderSubtle,
                shape = CircleShape,
            )
    if (interactionSource != null) {
        visualModifier = visualModifier.indication(interactionSource, ripple())
    }
    Box(
        modifier = visualModifier,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
