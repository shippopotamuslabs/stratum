package com.stratum.core.ui.component

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

/**
 * Stratum's primary call-to-action button. Wraps Material 3 [Button] using
 * `StratumTheme` semantic roles so feature code never imports raw M3 button APIs.
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
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchHeight),
        enabled = enabled,
        shape = StratumThemeAccessor.shapes.button,
        colors = ButtonDefaults.buttonColors(
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
