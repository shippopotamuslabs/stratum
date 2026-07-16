package com.stratum.core.ui.component.workoutlog

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.LocalStratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * Scrolls a focused cell so it sits above the keyboard/dock overlay. Implemented by the list
 * host (e.g. [LazyListState.animateScrollBy]); cells only supply window bounds.
 */
fun interface StratumOverlayDockRevealScroller {
    /**
     * Scroll the list so [cellBoundsInWindow] sits fully above the dock.
     * No-op when the cell is already clear of the dock.
     */
    suspend fun ensureCellAboveDock(cellBoundsInWindow: Rect)
}

/**
 * Shared bottom-dock host for custom keyboards / editors that overlay a scrollable list.
 * Lists add [dockHeightDp] to contentPadding and register [revealScroller].
 *
 * Open/closed is owned by [ProvideStratumOverlayDock] from `dockContent != null` — hosts only
 * set session state (which drives dockContent) and call [requestReveal] for in-session field tabs.
 */
@Stable
class StratumOverlayDockHostState {
    /**
     * Total height of the dock (content + nav bar) while open, else 0. Host lists should add
     * this to LazyColumn **contentPadding** (not Modifier.padding) so low fields can scroll up.
     */
    var dockHeightDp: Dp by mutableStateOf(0.dp)
        internal set

    /** Window Y of the top edge of the dock content while open (for reveal math). */
    var dockTopInWindow: Float by mutableFloatStateOf(Float.NaN)
        internal set

    /** List-level scroller. Set by consumers that own a LazyList. */
    var revealScroller: StratumOverlayDockRevealScroller? by mutableStateOf(null)

    /** Bumps when a dock session opens or active field changes so cells re-request reveal. */
    var revealEpoch: Int by mutableIntStateOf(0)
        private set

    /**
     * True while [ProvideStratumOverlayDock] is composing non-null dock content.
     * Do not set from feature hosts — derived by the provider.
     */
    var isOpen: Boolean by mutableStateOf(false)
        internal set

    /**
     * When true, the next dock collapse skips [rememberOverlayDockListBinding]'s
     * scroll-position restore. Set before dismissing for a successful set-complete so
     * post-log autoscroll is not cancelled by `scrollToItem(anchor)`.
     */
    var skipNextCollapseScrollRestore: Boolean = false

    /** Bump [revealEpoch] (e.g. field tab within a session). */
    fun requestReveal() {
        revealEpoch++
    }

    internal fun applyProviderOpen(open: Boolean) {
        if (open == isOpen) return
        isOpen = open
        if (open) {
            revealEpoch++
        } else {
            dockTopInWindow = Float.NaN
            dockHeightDp = 0.dp
        }
    }
}

val LocalStratumOverlayDock =
    staticCompositionLocalOf<StratumOverlayDockHostState?> { null }

/** Height of the upward-projecting shadow drawn above the dock's top edge (not layout). */
val OverlayDockTopShadowHeight = 12.dp

/**
 * Provides a [StratumOverlayDockHostState] and draws an in-hierarchy bottom dock **over** the
 * list when [dockContent] is non-null. Lists must add [StratumOverlayDockHostState.dockHeightDp]
 * to their own contentPadding so fields can scroll above the dock.
 *
 * Open state is derived solely from [dockContent] presence (via [SideEffect]).
 *
 * @param host Optional shared state (e.g. from [SetLogKeyboardHostState.overlay]). When null,
 * a new state is remembered for this composition.
 * @param dockContent Dock UI while open; null while closed. Composed at the physical bottom
 * with surfaceRaised background and navigation-bar padding.
 */
@Composable
fun ProvideStratumOverlayDock(
    dockContent: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    host: StratumOverlayDockHostState? = null,
    content: @Composable (listModifier: Modifier, dock: StratumOverlayDockHostState) -> Unit,
) {
    val remembered = remember { StratumOverlayDockHostState() }
    val dockHost = host ?: remembered
    val density = LocalDensity.current
    val isDark = LocalStratumTheme.current.dark
    val topShadowEndAlpha = if (isDark) 0.45f else 0.16f
    val shadowHeightPx = with(density) { OverlayDockTopShadowHeight.toPx() }

    // Single source of truth: content presence owns open/closed + reveal epoch on open.
    SideEffect {
        dockHost.applyProviderOpen(open = dockContent != null)
    }

    CompositionLocalProvider(LocalStratumOverlayDock provides dockHost) {
        Box(modifier = modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize(), dockHost)
            if (dockContent != null) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            // Shadow projects upward from the top edge without layout/hit targets.
                            .drawWithContent {
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colors =
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = topShadowEndAlpha),
                                                ),
                                            startY = -shadowHeightPx,
                                            endY = 0f,
                                        ),
                                    topLeft = Offset(0f, -shadowHeightPx),
                                    size = Size(size.width, shadowHeightPx),
                                )
                                drawContent()
                            }
                            .background(StratumThemeAccessor.colors.surfaceRaised)
                            .onSizeChanged { size ->
                                dockHost.dockHeightDp = with(density) { size.height.toDp() }
                            }
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .onGloballyPositioned { coords ->
                                dockHost.dockTopInWindow = coords.boundsInWindow().top
                            },
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        dockContent()
                    }
                }
            }
        }
    }
}

/**
 * Binds a [LazyListState] to an overlay dock: content-padding height, reveal scroller, and
 * scroll-anchor restore when the dock closes. Returns [StratumOverlayDockHostState.dockHeightDp]
 * for LazyColumn contentPadding.
 */
@Composable
fun rememberOverlayDockListBinding(
    listState: LazyListState,
    dock: StratumOverlayDockHostState?,
    revealMargin: Dp = OverlayDockDefaultRevealMargin,
): Dp {
    val density = LocalDensity.current
    val dockHeightDp = dock?.dockHeightDp ?: 0.dp
    val dockHeightPx = with(density) { dockHeightDp.toPx() }
    val revealMarginPx = with(density) { revealMargin.toPx() }
    var previousDockHeightPx by remember { mutableFloatStateOf(0f) }
    var anchorIndex by remember { mutableIntStateOf(0) }
    var anchorOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(dockHeightPx, listState.isScrollInProgress) {
        if (dockHeightPx > 0f && !listState.isScrollInProgress) {
            anchorIndex = listState.firstVisibleItemIndex
            anchorOffset = listState.firstVisibleItemScrollOffset
        }
    }

    LaunchedEffect(dockHeightPx) {
        val prev = previousDockHeightPx
        if (prev > 0f && dockHeightPx == 0f) {
            awaitFrame()
            // Completing a set dismisses the pad and then may animate to the next lift.
            // Restoring the pre-dismiss anchor here races that animation and cancels it
            // (common when check is hit quickly with only one field typed).
            val skipRestore = dock?.skipNextCollapseScrollRestore == true
            if (dock != null) {
                dock.skipNextCollapseScrollRestore = false
            }
            if (!skipRestore) {
                listState.scrollToItem(anchorIndex, anchorOffset)
            }
        }
        previousDockHeightPx = dockHeightPx
    }

    DisposableEffect(listState, dock, revealMarginPx) {
        if (dock == null) {
            return@DisposableEffect onDispose { }
        }
        dock.revealScroller =
            StratumOverlayDockRevealScroller { cellBounds ->
                awaitFrame()
                val dockTop = dock.dockTopInWindow
                if (!dockTop.isFinite()) return@StratumOverlayDockRevealScroller
                val overflow = cellBounds.bottom + revealMarginPx - dockTop
                if (overflow > 1f) {
                    // Short tween — default spring feels sluggish for ~⅓-screen keyboard jumps.
                    listState.animateScrollBy(
                        overflow,
                        animationSpec = tween(durationMillis = OVERLAY_DOCK_REVEAL_SCROLL_MS),
                    )
                }
                anchorIndex = listState.firstVisibleItemIndex
                anchorOffset = listState.firstVisibleItemScrollOffset
            }
        onDispose { dock.revealScroller = null }
    }

    return dockHeightDp
}

/**
 * Coordinate-based reveal: when [isActive], wait until the dock is open and laid out, then
 * ask the list scroller to place [cellBoundsInWindow] fully above the dock.
 *
 * **Do not** key the effect on [cellBoundsInWindow]. Bounds update every frame while the list
 * scrolls (`onGloballyPositioned`); keying on them cancels [LazyListState.animateScrollBy]
 * mid-flight and restarts with the remaining delta — the scroll crawls instead of finishing.
 * Latest bounds are read once via [rememberUpdatedState] when the dock is ready.
 */
@Composable
fun DockCellRevealEffect(
    isActive: Boolean,
    cellBoundsInWindow: Rect,
) {
    val overlayDock = LocalStratumOverlayDock.current
    val latestBounds by rememberUpdatedState(cellBoundsInWindow)
    // Only re-run when focus / field-tab / open-session changes — not on every layout pass.
    LaunchedEffect(isActive, overlayDock?.revealEpoch) {
        if (!isActive || overlayDock == null) return@LaunchedEffect
        snapshotFlow { overlayDock.isOpen }
            .distinctUntilChanged()
            .filter { it }
            .first()
        snapshotFlow { overlayDock.dockTopInWindow }
            .filter { it.isFinite() }
            .first()
        // Wait until dock height is reported so list contentPadding includes scroll room.
        snapshotFlow { overlayDock.dockHeightDp }
            .filter { it > 0.dp }
            .first()
        // One frame after padding applies so LazyColumn max scroll range is up to date.
        awaitFrame()
        val bounds =
            snapshotFlow { latestBounds }
                .filter { it != Rect.Zero && it.height > 0f }
                .first()
        val scroller =
            snapshotFlow { overlayDock.revealScroller }
                .filter { it != null }
                .first()
        scroller?.ensureCellAboveDock(bounds)
    }
}

/** Default gap kept between focused cell bottom and dock top when revealing. */
val OverlayDockDefaultRevealMargin = 8.dp

/** Duration for list reveal scroll when a focused cell is covered by the dock. */
private const val OVERLAY_DOCK_REVEAL_SCROLL_MS = 220
