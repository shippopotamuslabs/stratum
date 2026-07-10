package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Blocking confirm dialog with title, body, dismiss + confirm actions. Uses Stratum surface
 * chrome (not raw Material AlertDialog). When [destructiveConfirm] is true, the confirm action
 * uses error/destructive styling.
 */
@Composable
fun StratumConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructiveConfirm: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Dialog(onDismissRequest = onDismiss) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                StratumOutlinedButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    enabled = confirmEnabled,
                )
                StratumButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    destructive = destructiveConfirm,
                )
            }
        }
    }
}
