package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scaffold variant for screens hosted inside `MainTabShell` (or any shell that already reserves
 * the navigation-bar inset via its own padding). Differences from [StratumScaffold]:
 *
 * - **`contentWindowInsets` is always `WindowInsets(0)`** — the shell owns the bottom inset;
 *   screens must not consume it again or a double-gap appears above the nav bar.
 * - Wraps content in a `Column` that applies only `top` / `bottom` from the scaffold's
 *   `PaddingValues` (resolves to the top-bar height only, since insets are zeroed) plus
 *   [horizontalPadding] (default 12dp — close to the glass, matching the top-app-bar feel).
 * - With `overlayTopBar` the top padding is skipped: content scrolls under a glass top
 *   bar (§12.8) and the screen pads its own scrollable's start by the bar height.
 *
 * Usage:
 * ```kotlin
 * StratumTabScaffold(topBar = { … }) {
 *     // ColumnScope — no inset/padding boilerplate needed
 * }
 * ```
 */
@Composable
fun StratumTabScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    horizontalPadding: Dp = 12.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    overlayTopBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        // Overlay mode (§12.8 glass top bar): content flows UNDER the top bar so the
        // glass has something to blur; the screen's own scrollable then owns the top
        // clearance (StratumGlassChrome.topBarHeight) as content padding.
        val topPadding = if (overlayTopBar) 0.dp else paddingValues.calculateTopPadding()
        val colModifier = Modifier
            .fillMaxSize()
            .padding(
                top = topPadding,
                bottom = paddingValues.calculateBottomPadding(),
            )
            .padding(horizontal = horizontalPadding)
        Column(
            modifier = colModifier,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
