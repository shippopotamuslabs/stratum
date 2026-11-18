package com.stratum.core.ui.component

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumSpacing
import kotlinx.coroutines.flow.first

/**
 * Horizontal strip the caller syncs with its own scrolling list.
 *
 * [itemContent] owns taps and selection chrome. This strip does not scroll the
 * caller's list and does not pick a landing offset. When [selectedIndex] changes
 * it scrolls only far enough to keep that item on screen; an item that is already
 * fully visible does not move. An item wider than the strip keeps its start in
 * view. A forward item that is not yet composed is brought in by its trailing
 * edge, not pinned to the leading edge. [trailingContent] is a separate slot and
 * is not a selectable index.
 */
@Composable
fun StratumSyncedItemStrip(
    itemCount: Int,
    selectedIndex: Int?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = StratumSpacing.s16),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(StratumSpacing.s4),
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    itemKey: (index: Int) -> Any = { it },
    trailingKey: Any = "stratum-synced-strip-trailing",
    trailingContent: (@Composable () -> Unit)? = null,
    itemContent: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    LaunchedEffect(selectedIndex) {
        val index = selectedIndex ?: return@LaunchedEffect
        // Count from the composition that applied this selection. Keying the
        // effect on itemCount would pull the strip back when a trailing add
        // appends an item the user had scrolled to reach.
        val count = itemCount
        if (index !in 0 until count) return@LaunchedEffect
        state.revealSelectedStripItem(index)
    }

    LazyRow(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
    ) {
        items(count = itemCount, key = itemKey) { index ->
            itemContent(index, index == selectedIndex)
        }
        if (trailingContent != null) {
            item(key = trailingKey) {
                trailingContent()
            }
        }
    }
}

private suspend fun LazyListState.revealSelectedStripItem(index: Int) {
    // One shot per selection. Collecting layout forever would fight a manual drag.
    val layout =
        snapshotFlow { layoutInfo }
            .first { it.totalItemsCount > 0 }
    val composed = layout.visibleItemsInfo.firstOrNull { it.index == index }
    if (composed != null) {
        animateComposedStripReveal(index, composed, layout)
    } else {
        revealOffscreenStripItem(index, layout)
    }
}

private suspend fun LazyListState.revealOffscreenStripItem(
    index: Int,
    layout: LazyListLayoutInfo,
) {
    val visible = layout.visibleItemsInfo
    val anchor = stripRevealAnchor(visible, index) ?: return
    val startedForward = visible.isNotEmpty() && index > visible.last().index
    animateOffscreenStripReveal(index, anchor, visible, layout)
    if (startedForward) {
        settleForwardStripReveal(index)
    } else {
        settleBackwardStripReveal(index)
    }
}

/**
 * Forward jumps end-align once the item is composed, including a negative
 * correction when a wide anchor overshot. [animateScrollToItem] is refused
 * here: it would pin the target to the leading edge and the landing delta
 * would not undo a fully on-screen pin.
 */
private suspend fun LazyListState.settleForwardStripReveal(index: Int) {
    if (correctForwardLanding(index)) return
    val after = layoutInfo
    val visibleAfter = after.visibleItemsInfo
    val nextAnchor = stripRevealAnchor(visibleAfter, index) ?: return
    if (nextAnchor.index > index) {
        animateOvershotStripReveal(index, nextAnchor, visibleAfter, after)
    } else {
        animateOffscreenStripReveal(index, nextAnchor, visibleAfter, after)
    }
    correctForwardLanding(index)
}

private suspend fun LazyListState.animateOvershotStripReveal(
    index: Int,
    anchor: LazyListItemInfo,
    visible: List<LazyListItemInfo>,
    layout: LazyListLayoutInfo,
) {
    val delta =
        stripRevealOvershootDelta(
            targetIndex = index,
            anchorIndex = anchor.index,
            anchorOffsetPx = anchor.offset,
            stepSizePx = visible.minOf { it.size },
            spacingPx = stripVisibleSpacingPx(visible),
            viewportEndPx = layout.viewportEndOffset,
        )
    if (delta != 0) {
        animateScrollBy(delta.toFloat())
    }
}

/** Backward jumps may pin to the leading edge. That is the minimum scroll. */
private suspend fun LazyListState.settleBackwardStripReveal(index: Int) {
    if (correctComposedStripReveal(index)) return
    val firstAfter = layoutInfo.visibleItemsInfo.firstOrNull() ?: return
    if (index < firstAfter.index) {
        animateScrollToItem(index)
        correctComposedStripReveal(index)
    }
}

/**
 * Applies [stripRevealForwardLandingDelta] when [index] is composed.
 * Returns false when it is not.
 */
private suspend fun LazyListState.correctForwardLanding(index: Int): Boolean {
    val landed = layoutInfo
    val item = landed.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    val delta =
        stripRevealForwardLandingDelta(
            itemOffsetPx = item.offset,
            itemSizePx = item.size,
            viewportStartPx = landed.viewportStartOffset,
            viewportEndPx = landed.viewportEndOffset,
        )
    if (delta != 0) {
        animateScrollBy(delta.toFloat())
    }
    return true
}

/** Applies [stripReveal] when [index] is composed. Returns false when it is not. */
private suspend fun LazyListState.correctComposedStripReveal(index: Int): Boolean {
    val landed = layoutInfo
    val item = landed.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    animateComposedStripReveal(index, item, landed)
    return true
}

private suspend fun LazyListState.animateComposedStripReveal(
    index: Int,
    item: LazyListItemInfo,
    layout: LazyListLayoutInfo,
) {
    val reveal =
        stripReveal(
            itemIndex = index,
            itemOffsetPx = item.offset,
            itemSizePx = item.size,
            viewportStartPx = layout.viewportStartOffset,
            viewportEndPx = layout.viewportEndOffset,
        )
    if (reveal.scrollByPx != 0) {
        animateScrollBy(reveal.scrollByPx.toFloat())
    }
}

private suspend fun LazyListState.animateOffscreenStripReveal(
    index: Int,
    anchor: LazyListItemInfo,
    visible: List<LazyListItemInfo>,
    layout: LazyListLayoutInfo,
) {
    val delta =
        stripRevealOffscreenDelta(
            targetIndex = index,
            anchorIndex = anchor.index,
            anchorOffsetPx = anchor.offset,
            anchorSizePx = anchor.size,
            stepSizePx = visible.minOf { it.size },
            spacingPx = stripVisibleSpacingPx(visible),
            viewportStartPx = layout.viewportStartOffset,
            viewportEndPx = layout.viewportEndOffset,
        )
    if (delta != 0) {
        animateScrollBy(delta.toFloat())
    }
}

/**
 * Last visible item when [targetIndex] is past it, otherwise the first.
 * Empty layout has no anchor.
 */
private fun stripRevealAnchor(
    visible: List<LazyListItemInfo>,
    targetIndex: Int,
): LazyListItemInfo? {
    if (visible.isEmpty()) return null
    val last = visible.last()
    return if (targetIndex > last.index) last else visible.first()
}

/**
 * Gap between the first two visible items. Zero when fewer than two are composed.
 * Measured from offsets so the strip does not depend on a main-axis spacing API.
 */
private fun stripVisibleSpacingPx(visible: List<LazyListItemInfo>): Int {
    if (visible.size < 2) return 0
    val first = visible.first()
    val second = visible[1]
    return (second.offset - (first.offset + first.size)).coerceAtLeast(0)
}
