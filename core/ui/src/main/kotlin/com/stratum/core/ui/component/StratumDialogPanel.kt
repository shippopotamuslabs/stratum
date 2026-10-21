package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Shared blocking-dialog panel: Stratum surface chrome plus title/body rows. The
 * caller supplies the action row so confirm and choice dialogs share pixels while
 * keeping their distinct dismiss semantics.
 */
@Composable
internal fun StratumDialogPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // §12.3: dialogs sit on surface-3 with the extraLarge (28dp) shape.
                .background(color = colors.surfaceInset, shape = shapes.extraLarge)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderStrong,
                    shape = shapes.extraLarge,
                )
                .padding(StratumSpacing.s24),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumText(
            text = body,
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
        )
        content?.invoke(this)
        DialogActionRow(actions = actions)
    }
}

@Composable
private fun DialogActionRow(actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        // §12.3: dialog action buttons sit 8dp apart.
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8, Alignment.End),
        content = actions,
    )
}
