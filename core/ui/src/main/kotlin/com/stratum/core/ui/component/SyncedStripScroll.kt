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
 * The pitch is the anchor's size plus [spacingPx], measured by the caller from
 * visible items. This does not read a list spacing API.
 */
internal fun stripRevealOffscreenDelta(
    targetIndex: Int,
    anchorIndex: Int,
    anchorOffsetPx: Int,
    anchorSizePx: Int,
    spacingPx: Int,
    viewportStartPx: Int,
    viewportEndPx: Int,
): Int {
    val steps = targetIndex - anchorIndex
    if (steps > 0) {
        val estimatedTargetEnd = anchorOffsetPx + anchorSizePx + steps * (anchorSizePx + spacingPx)
        return estimatedTargetEnd - viewportEndPx
    }
    if (steps < 0) {
        val estimatedTargetStart =
            anchorOffsetPx - (anchorIndex - targetIndex) * (anchorSizePx + spacingPx)
        return estimatedTargetStart - viewportStartPx
    }
    return 0
}
