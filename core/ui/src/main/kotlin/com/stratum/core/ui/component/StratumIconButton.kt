package com.stratum.core.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
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
        // §12.3: 40x40dp container. Appended after the caller's modifier so a
        // caller-set size (earlier in the chain) still wins deliberately.
        modifier = modifier.size(40.dp),
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
