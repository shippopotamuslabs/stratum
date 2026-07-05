package com.stratum.core.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Circular icon button for secondary actions in Stratum chrome.
 */
@Composable
fun StratumIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    contentColor: Color = StratumThemeAccessor.colors.textPrimary,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = StratumThemeAccessor.colors.borderSubtle,
                disabledContentColor = StratumThemeAccessor.colors.textMuted,
            ),
    ) {
        content()
    }
}
