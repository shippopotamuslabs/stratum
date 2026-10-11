package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
 * Blocking two-option decision dialog. Unlike [StratumConfirmDialog], backdrop cancel is a
 * distinct outcome ([onDismissRequest]) — neither option is taken implicitly. Use when the
 * caller must distinguish "chose option B" from "dismissed without choosing".
 */
@Composable
fun StratumChoiceDialog(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    destructivePrimary: Boolean = false,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        StratumChoiceDialogContent(
            title = title,
            body = body,
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            secondaryLabel = secondaryLabel,
            onSecondary = onSecondary,
            destructivePrimary = destructivePrimary,
            modifier = modifier,
        )
    }
}

/** Dialog body without the window, so previews and screenshot tests can render it inline. */
@Composable
fun StratumChoiceDialogContent(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    destructivePrimary: Boolean = false,
) {
    StratumDialogPanel(
        title = title,
        body = body,
        modifier = modifier,
    ) {
        StratumOutlinedButton(
            text = secondaryLabel,
            onClick = onSecondary,
        )
        StratumButton(
            text = primaryLabel,
            onClick = onPrimary,
            destructive = destructivePrimary,
        )
    }
}
