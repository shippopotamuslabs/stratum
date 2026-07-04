package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Overflow / context menu chrome for Stratum top bars. Wraps M3 [DropdownMenu] with semantic
 * surface colors so feature code never imports raw M3 menu APIs.
 */
@Composable
fun StratumDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier =
            modifier.background(
                color = colors.surfaceRaised,
                shape = StratumThemeAccessor.shapes.panel,
            ),
        containerColor = colors.surfaceRaised,
        content = content,
    )
}

@Composable
fun StratumDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = StratumThemeAccessor.typography.bodyLarge,
                color = StratumThemeAccessor.colors.textPrimary,
            )
        },
        leadingIcon = leadingIcon,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}
