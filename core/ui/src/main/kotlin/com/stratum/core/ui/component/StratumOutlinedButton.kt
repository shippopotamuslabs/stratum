package com.stratum.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumButtonHardShadow

/**
 * Secondary call-to-action with transparent fill and outline — e.g. "Start Empty Workout".
 *
 * When [destructive] is true, border and label use the error semantic role (e.g. discard actions).
 * Neo Brutalist themes apply a hard-offset shadow from elevations; Tonal stays flat.
 */
@Composable
fun StratumOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = StratumThemeAccessor.typography.bodyLarge,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    containerColor: Color = Color.Transparent,
    /** When true, uses error semantic colors for border and label. */
    destructive: Boolean = false,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations
    val borderColor =
        when {
            destructive -> colors.error
            elevations.useHardShadow -> colors.borderStrong
            else -> colors.accent
        }
    val contentColor = if (destructive) colors.error else colors.accent
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = MinTouchHeight)
                .stratumButtonHardShadow(enabled = enabled, shape = shapes.button),
        enabled = enabled,
        shape = shapes.button,
        border = BorderStroke(shapes.borderWidth, borderColor),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = colors.textMuted,
            ),
        contentPadding = contentPadding,
    ) {
        Text(text = text, style = style)
    }
}

private val MinTouchHeight = 48.dp
