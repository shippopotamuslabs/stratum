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

/**
 * Secondary call-to-action with transparent fill and accent outline — e.g. "Start Empty Workout".
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
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchHeight),
        enabled = enabled,
        shape = shapes.button,
        border = BorderStroke(shapes.borderWidth, colors.accent),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = colors.accent,
                disabledContainerColor = containerColor,
                disabledContentColor = colors.textMuted,
            ),
        contentPadding = contentPadding,
    ) {
        Text(text = text, style = style)
    }
}

private val MinTouchHeight = 48.dp
