package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderStrong,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
