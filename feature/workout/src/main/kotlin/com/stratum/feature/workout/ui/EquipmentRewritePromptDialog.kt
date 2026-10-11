package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.component.StratumChoiceDialog
import com.stratum.core.ui.component.StratumChoiceDialogContent
import com.stratum.feature.workout.R

/**
 * Partial-swap prompt (STR-122): logged sets exist on the lift, so the user decides
 * whether they keep the old equipment or are rewritten to the newly selected piece.
 * Backdrop cancel chooses neither — the swap is not applied.
 */
@Composable
internal fun EquipmentRewritePromptDialog(
    prompt: EquipmentRewritePromptUi,
    onRewrite: () -> Unit,
    onKeepOld: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumChoiceDialog(
        title = stringResource(R.string.equipment_rewrite_title),
        body =
            pluralStringResource(
                R.plurals.equipment_rewrite_body,
                prompt.loggedCount,
                prompt.loggedCount,
                prompt.oldName ?: stringResource(R.string.equipment_rewrite_unnamed_old),
                prompt.newName,
            ),
        primaryLabel = stringResource(R.string.equipment_rewrite_apply),
        onPrimary = onRewrite,
        secondaryLabel = stringResource(R.string.equipment_rewrite_keep),
        onSecondary = onKeepOld,
        onDismissRequest = onDismiss,
    )
}

/**
 * Prompt body without the window, so previews and screenshot tests can render it inline.
 * Public for the `:app` screenshot test (same-module callers use [EquipmentRewritePromptDialog]).
 */
@Composable
fun EquipmentRewritePromptContent(
    prompt: EquipmentRewritePromptUi,
) {
    StratumChoiceDialogContent(
        title = stringResource(R.string.equipment_rewrite_title),
        body =
            pluralStringResource(
                R.plurals.equipment_rewrite_body,
                prompt.loggedCount,
                prompt.loggedCount,
                prompt.oldName ?: stringResource(R.string.equipment_rewrite_unnamed_old),
                prompt.newName,
            ),
        primaryLabel = stringResource(R.string.equipment_rewrite_apply),
        onPrimary = {},
        secondaryLabel = stringResource(R.string.equipment_rewrite_keep),
        onSecondary = {},
    )
}
