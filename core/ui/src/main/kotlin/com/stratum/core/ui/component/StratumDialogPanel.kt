package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderStrong,
                    shape = shapes.panel,
                )
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        DialogActionRow(actions = actions)
    }
}

@Composable
private fun DialogActionRow(actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        content = actions,
    )
}
