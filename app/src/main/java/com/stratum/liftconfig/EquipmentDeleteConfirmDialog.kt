package com.stratum.liftconfig

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumConfirmDialog

/**
 * Shared delete confirmation for both equipment delete entry points (Manage
 * Equipment swipe-to-dismiss and the increments-editor red delete button), so
 * title/body/label copy and destructive styling cannot drift between them.
 */
@Composable
internal fun EquipmentDeleteConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumConfirmDialog(
        title = stringResource(UiR.string.equipment_delete_title, name),
        body = stringResource(UiR.string.equipment_delete_body, name),
        confirmLabel = stringResource(UiR.string.equipment_delete_confirm),
        dismissLabel = stringResource(UiR.string.equipment_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructiveConfirm = true,
    )
}
