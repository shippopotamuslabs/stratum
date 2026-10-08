package com.stratum.feature.program.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Shared "discard live session and start" dialog used by Programs and Edit Workout.
 */
@Composable
fun StartSessionDiscardDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        StartSessionDiscardDialogContent(
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

/**
 * Active-session discard dialog body without the dialog window. Screenshot tests cannot
 * capture dialog windows (an open dialog blanks the golden), so goldens pin this content
 * inline (see docs/screenshot-tests.md).
 */
@Composable
internal fun StartSessionDiscardDialogContent(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DialogPanelContent {
        StratumText(
            text = stringResource(R.string.programs_start_discard_title),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumText(
            text = stringResource(R.string.programs_start_discard_body),
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumButton(
                text = stringResource(R.string.programs_start_discard_confirm),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
            StratumOutlinedButton(
                text = stringResource(R.string.programs_start_discard_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
