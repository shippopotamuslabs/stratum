package com.stratum.core.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.GlassChromePadding
import com.stratum.core.ui.theme.rememberGlassChromePadding

/**
 * The detail-route preset of the glass seam (STR-157): [StratumTabScaffold]
 * with the geometry every detail screen outside `MainTabShell` shares, kept
 * in exactly one place so call sites cannot drift apart:
 *
 * - **Detail insets** — `StratumScaffold`'s default (`ScaffoldDefaults` minus
 *   the navigation bars), passed through the scaffold's internal overload.
 *   Solid mode therefore reproduces the geometry these screens had on
 *   `StratumScaffold` pixel for pixel; overlay mode owns the top inset
 *   through the clearance formula instead. The tab scaffold's public
 *   invariant (shell-owned `WindowInsets(0)`) is untouched.
 * - **No horizontal padding** — detail screens own their content padding.
 * - **Overlay wired** — `overlayTopBar` follows the glass host's presence,
 *   and the [GlassChromePadding] snapshot is handed to the content so each
 *   screen applies the top clearance where its own geometry puts it (on the
 *   scrollable, or on the column above a fixed header). `chrome.top` is 0
 *   whenever glass is inactive, so solid layouts need no branching.
 */
@Composable
fun StratumDetailScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable ColumnScope.(GlassChromePadding) -> Unit,
) {
    val chrome = rememberGlassChromePadding()
    StratumTabScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHostState = snackbarHostState,
        horizontalPadding = 0.dp,
        overlayTopBar = chrome.active,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
    ) {
        content(chrome)
    }
}
