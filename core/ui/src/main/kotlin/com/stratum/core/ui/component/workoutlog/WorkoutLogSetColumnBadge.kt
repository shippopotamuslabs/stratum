package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/** Shared size for set-type badges and the add-set + control so the Set column stays aligned. */
internal val WorkoutLogSetColumnBadgeSize = 32.dp

/**
 * Circular set-column badge matching template Edit Workout. Used for set-type letters and the
 * add-set `+` so both share the same left edge and visual center under the "Set" header.
 */
@Composable
internal fun WorkoutLogSetColumnBadge(
    modifier: Modifier = Modifier,
    backgroundColor: Color = StratumThemeAccessor.colors.surfaceInset,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(WorkoutLogSetColumnBadgeSize)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = CircleShape,
                )
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
