// Holds StripReveal, stripReveal, and stripRevealOffscreenDelta; the file name is the helper.
@file:Suppress("MatchingDeclarationName")

package com.stratum.core.ui.component

internal data class StripReveal(
    val scrollToIndex: Int?,
    val scrollByPx: Int,
)

/**
 * How to bring item [itemIndex] fully into the strip viewport.
 *
 * Null offset or size means the item is not composed: [StripReveal.scrollToIndex]
 * is that index and [StripReveal.scrollByPx] is 0. Callers must not turn that
 * result into [androidx.compose.foundation.lazy.LazyListState.animateScrollToItem]
 * for a forward target — that pins the item to the leading edge.
 * [stripRevealOffscreenDelta] is the minimum scroll for an uncomposed item.
 *
 * A composed item wider than the viewport always start-aligns, including when
 * its start is already inside and only the end is clipped. End-aligning a wide
 * item slides the start off the leading edge. A narrower item scrolls by its
 * leading overflow, else its trailing overflow, else not at all.
 */
internal fun stripReveal(
    itemIndex: Int,
    itemOffsetPx: Int?,
    itemSizePx: Int?,
    viewportStartPx: Int,
    viewportEndPx: Int,
): StripReveal {
    if (itemOffsetPx == null || itemSizePx == null) {
        return StripReveal(scrollToIndex = itemIndex, scrollByPx = 0)
    }
    val itemEndPx = itemOffsetPx + itemSizePx
    val scrollByPx =
        when {
            itemSizePx > viewportEndPx - viewportStartPx -> itemOffsetPx - viewportStartPx
            itemOffsetPx < viewportStartPx -> itemOffsetPx - viewportStartPx
            itemEndPx > viewportEndPx -> itemEndPx - viewportEndPx
            else -> 0
        }
    return StripReveal(scrollToIndex = null, scrollByPx = scrollByPx)
}

/**
 * Minimum scroll, in px, to reveal an uncomposed [targetIndex] from a visible anchor.
 *
 * [targetIndex] must differ from [anchorIndex]. Forward (target after the anchor)
 * returns the overflow of the estimated end past [viewportEndPx], the smallest
 * scroll that puts that end on the viewport end. Backward start-aligns the
 * estimated start to [viewportStartPx]. A zero step is not a scroll.
 *
 * [anchorSizePx] is the anchor's own size, so its end is real. [stepSizePx] is
 * the pitch of the items between the anchor and the target. Callers pass the
 * narrowest visible size on a forward jump so a wide anchor undershoots and
 * [stripRevealForwardLandingDelta] can finish on the trailing edge. Using the
 * anchor's size as the pitch overshoots when later items are narrower, and the
 * composed item then looks fully visible.
 */
internal fun stripRevealOffscreenDelta(
    targetIndex: Int,
    anchorIndex: Int,
    anchorOffsetPx: Int,
    anchorSizePx: Int,
    stepSizePx: Int,
    spacingPx: Int,
    viewportStartPx: Int,
    viewportEndPx: Int,
): Int {
    val steps = targetIndex - anchorIndex
    if (steps > 0) {
        val estimatedTargetEnd = anchorOffsetPx + anchorSizePx + steps * (stepSizePx + spacingPx)
        return estimatedTargetEnd - viewportEndPx
    }
    if (steps < 0) {
        val estimatedTargetStart =
            anchorOffsetPx - (anchorIndex - targetIndex) * (stepSizePx + spacingPx)
        return estimatedTargetStart - viewportStartPx
    }
    return 0
}

/**
 * Where a forward off-screen item should sit once it is composed.
 *
 * Not for an item that was already visible: [stripReveal] leaves that item
 * alone. A forward jump's minimum scroll leaves a fitting item's end on
 * [viewportEndPx], including a negative delta when the estimate overshot.
 * An item wider than the viewport start-aligns instead, so its leading edge
 * stays on screen.
 */
internal fun stripRevealForwardLandingDelta(
    itemOffsetPx: Int,
    itemSizePx: Int,
    viewportStartPx: Int,
    viewportEndPx: Int,
): Int {
    if (itemSizePx > viewportEndPx - viewportStartPx) {
        return itemOffsetPx - viewportStartPx
    }
    return itemOffsetPx + itemSizePx - viewportEndPx
}

/**
 * Scroll that puts an overshot forward target's end on [viewportEndPx].
 *
 * The anchor is a visible item after [targetIndex] (the jump scrolled past
 * the target). [stepSizePx] is the narrowest visible size. This is not a
 * start-align: pinning the leading edge is the wrong minimum for a target
 * that began ahead of the viewport.
 */
internal fun stripRevealOvershootDelta(
    targetIndex: Int,
    anchorIndex: Int,
    anchorOffsetPx: Int,
    stepSizePx: Int,
    spacingPx: Int,
    viewportEndPx: Int,
): Int {
    val steps = anchorIndex - targetIndex
    if (steps <= 0) return 0
    val estimatedTargetEnd = anchorOffsetPx - steps * (stepSizePx + spacingPx) + stepSizePx
    return estimatedTargetEnd - viewportEndPx
}
