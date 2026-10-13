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
    content: @Composable (androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        StratumDialogPanel(
            title = title,
            body = body,
            modifier = modifier,
            content = content,
        ) {
            StratumOutlinedButton(
                text = dismissLabel,
                onClick = onDismiss,
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
