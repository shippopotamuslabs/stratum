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
import com.stratum.core.ui.theme.rememberGlassHazeState
import dev.chrisbanes.haze.hazeSource

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
 * Glass seam (STR-155): the overlay request only takes effect while a shared glass
 * state is active ([rememberGlassHazeState] non-null — shell-provided via
 * `LocalStratumHazeState`, Tonal family). The shared `hazeSource` is applied to the
 * CONTENT side only, so the `topBar` (a Scaffold slot sibling, outside the source
 * subtree) blurs the content scrolling beneath it. A bar inside its own source
 * subtree has nothing behind it to blur; the seam keeps exactly that from happening.
 * Merge-mode and other solid bars pass `overlayTopBar = false` and stay in-flow.
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
    // The overlay request is honored only under an active shared glass state; a
    // screen asking for overlay outside a glass host (previews, Neo, detail routes)
    // keeps the legacy in-flow bar instead of a transparent one with no source.
    // The local keeps the state non-null past the check so the source modifier below
    // needs no assertion.
    val overlayState = if (overlayTopBar) rememberGlassHazeState() else null
    val overlay = overlayState != null
    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        // Overlay mode (§12.8 glass top bar): content flows UNDER the top bar so the
        // glass has something to blur; the screen's own scrollable then owns the top
        // clearance (StratumGlassChrome.topClearance) as content padding. The source lives on this
        // content column only — the topBar slot stays outside the source subtree.
        val topPadding = if (overlay) 0.dp else paddingValues.calculateTopPadding()
        val colModifier = Modifier
            .fillMaxSize()
            .then(if (overlayState != null) Modifier.hazeSource(state = overlayState) else Modifier)
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
