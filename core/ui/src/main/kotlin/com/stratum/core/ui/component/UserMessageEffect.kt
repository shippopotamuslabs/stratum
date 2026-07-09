package com.stratum.core.ui.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Shows a one-shot snackbar for [userMessage] and notifies the ViewModel when it has been
 * presented (Google UI-events pattern: message lives in UI state until consumed).
 *
 * Call from a screen that owns a [SnackbarHostState] wired into [StratumScaffold].
 */
@Composable
fun UserMessageEffect(
    userMessage: String?,
    snackbarHostState: SnackbarHostState,
    onUserMessageShown: () -> Unit,
) {
    LaunchedEffect(userMessage) {
        if (userMessage == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(userMessage)
        onUserMessageShown()
    }
}
