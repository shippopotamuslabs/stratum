package com.stratum.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumButtonHardShadow

/**
 * Stratum's primary call-to-action button. Wraps Material 3 [Button] using
 * `StratumTheme` semantic roles so feature code never imports raw M3 button APIs.
 *
 * Neo Brutalist themes apply a hard-offset shadow and strong border from
 * [com.stratum.core.ui.theme.StratumElevations]; Tonal keeps a flat filled button.
 */
@Composable
fun StratumButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = StratumThemeAccessor.typography.bodyLarge,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    /** When true, uses error semantic colors (destructive confirms). */
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations
    Button(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = MinTouchHeight)
                .stratumButtonHardShadow(enabled = enabled, shape = shapes.button),
        enabled = enabled,
        shape = shapes.button,
        border =
            if (elevations.useHardShadow) {
                BorderStroke(shapes.borderWidth, colors.borderStrong)
            } else {
                null
            },
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (destructive) colors.error else colors.accent,
                contentColor = if (destructive) colors.onError else colors.onAccent,
                disabledContainerColor =
                    if (destructive) colors.error.copy(alpha = 0.5f) else colors.accentMuted,
                disabledContentColor = if (destructive) colors.onError else colors.onAccent,
            ),
        contentPadding = contentPadding,
    ) {
        Text(text = text, style = style)
    }
}

private val MinTouchHeight = 48.dp
