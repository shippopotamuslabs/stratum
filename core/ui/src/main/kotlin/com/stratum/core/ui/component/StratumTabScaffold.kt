package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
 * - **`contentWindowInsets` defaults to `WindowInsets(0)`** — the tab shell owns the
 *   insets; screens must not consume them again or a double-gap appears above the
 *   nav bar. Detail hosts outside a shell (STR-157: the workout routes) pass
 *   `StratumScaffold`'s detail insets instead, so solid mode keeps the exact
 *   geometry those screens had on `StratumScaffold`; overlay mode ignores the
 *   top inset either way (the clearance formula owns it, §12.8).
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
 * [bottomOverlay] is the bottom twin of that seam (STR-156): a drawer or dock
 * rendered over the content's bottom edge, OUTSIDE the source subtree, so it can
 * carry sheet glass of its own. It anchors to the same bottom edge the content
 * column is padded to, so in-flow bottom bars keep reserving its space; under
 * glass the overlay itself owns any nav-clearance offset. Content hosted inside
 * the source can never play this role — the STR-155 field report's hidden
 * active-workout drawer is what happens when it tries.
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
    bottomOverlay: @Composable (() -> Unit)? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0),
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
        contentWindowInsets = contentWindowInsets,
    ) { paddingValues ->
        // Overlay mode (§12.8 glass top bar): content flows UNDER the top bar so the
        // glass has something to blur; the screen's own scrollable then owns the top
        // clearance (StratumGlassChrome.topClearance) as content padding. The source lives on this
        // content column only — the topBar slot stays outside the source subtree.
        val topPadding = if (overlay) 0.dp else paddingValues.calculateTopPadding()
        val bottomPadding = paddingValues.calculateBottomPadding()
        val colModifier = Modifier
            .fillMaxSize()
            .then(if (overlayState != null) Modifier.hazeSource(state = overlayState) else Modifier)
            .padding(
                top = topPadding,
                bottom = bottomPadding,
            )
            .padding(horizontal = horizontalPadding)
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = colModifier,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                content = content,
            )
            if (bottomOverlay != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomPadding),
                ) {
                    bottomOverlay()
                }
            }
        }
    }
}
