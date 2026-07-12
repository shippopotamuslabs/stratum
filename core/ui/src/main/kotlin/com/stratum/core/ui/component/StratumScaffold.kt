package com.stratum.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Default content insets exclude the navigation bar so full-screen routes (no bottom bar)
 * can draw edge-to-edge under the system gesture handle. Screens with a [bottomBar] still
 * reserve space via the bar itself; fixed bottom chrome should use
 * [androidx.compose.foundation.layout.navigationBarsPadding] on that chrome.
 */
@Composable
fun StratumScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    contentWindowInsets: WindowInsets =
        ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = StratumThemeAccessor.colors.surfaceBase,
        contentColor = StratumThemeAccessor.colors.textPrimary,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}
