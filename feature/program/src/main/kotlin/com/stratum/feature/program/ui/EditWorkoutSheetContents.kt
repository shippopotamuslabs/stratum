package com.stratum.feature.program.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

/**
 * Sheet title matching [StratumBottomSheet]'s built-in title styling. Sheet bodies render
 * their own title so screenshot goldens pin it inline (dialog windows are not capturable —
 * see docs/screenshot-tests.md); production wrappers pass `title = null`. Keep the style
 * in sync with [StratumBottomSheet].
 */
@Composable
internal fun SheetContentTitle(text: String) {
    StratumText(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(bottom = StratumSpacing.s8),
        style = StratumThemeAccessor.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
}

/**
 * Edit-workout discard sheet body without the dialog window. Screenshot tests cannot
 * capture dialog windows (an open sheet blanks the golden), so goldens pin this content
 * inline (see docs/screenshot-tests.md). The title lives here (not the sheet wrapper) so
 * the golden covers it; production pixels are unchanged (same text, style, and position).
 */
@Composable
internal fun EditWorkoutDiscardSheetContent(
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    SheetContentTitle(text = stringResource(R.string.edit_workout_discard_title))
    StratumText(
        text = stringResource(R.string.edit_workout_discard_message),
        style = StratumThemeAccessor.typography.bodyLarge,
    )
    StratumButton(
        text = stringResource(R.string.edit_workout_discard_confirm),
        onClick = onConfirmDiscard,
        modifier = Modifier.fillMaxWidth(),
    )
    StratumOutlinedButton(
        text = stringResource(R.string.edit_workout_discard_cancel),
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Dirty-start dialog body without the dialog window. Screenshot tests cannot capture
 * dialog windows (an open dialog blanks the golden), so goldens pin this content inline
 * (see docs/screenshot-tests.md).
 */
@Composable
internal fun EditWorkoutDirtyStartDialogContent(
    onConfirmSaveAndStart: () -> Unit,
    onDiscardAndStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogPanelContent {
        StratumText(
            text = stringResource(R.string.edit_workout_start_dirty_title),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumText(
            text = stringResource(R.string.edit_workout_start_dirty_body),
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        ) {
            StratumButton(
                text = stringResource(R.string.edit_workout_start_dirty_confirm),
                onClick = onConfirmSaveAndStart,
                modifier = Modifier.fillMaxWidth(),
            )
            StratumOutlinedButton(
                text = stringResource(R.string.edit_workout_start_dirty_discard),
                onClick = onDiscardAndStart,
                modifier = Modifier.fillMaxWidth(),
            )
            StratumOutlinedButton(
                text = stringResource(R.string.edit_workout_start_dirty_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
