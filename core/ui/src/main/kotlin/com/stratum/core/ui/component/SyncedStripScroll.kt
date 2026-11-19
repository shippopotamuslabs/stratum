// Holds StripReveal and stripReveal together; the file name is the helper, not the result type.
@file:Suppress("MatchingDeclarationName")

package com.stratum.core.ui.component

internal data class StripReveal(
    val scrollToIndex: Int?,
    val scrollByPx: Int,
)

/**
 * How to bring item [itemIndex] fully into the strip viewport.
 *
 * Null offset or size means the item is not composed: scroll to that index.
 * Otherwise return a pixel delta (negative reveals a left-clipped item,
 * positive a right-clipped one, 0 when already fully visible).
 * If the item is wider than the viewport and overlaps both edges,
 * align its start (the first branch).
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
    val overlapsStart = itemOffsetPx < viewportStartPx
    val overlapsEnd = itemEndPx > viewportEndPx
    val widerThanViewport = itemSizePx > viewportEndPx - viewportStartPx
    val scrollByPx =
        when {
            // Wider than the viewport and clipped on both edges: pin the start.
            // Checked before the end-overflow branch so a wide item is not end-aligned.
            widerThanViewport && overlapsStart && overlapsEnd -> itemOffsetPx - viewportStartPx
            overlapsStart -> itemOffsetPx - viewportStartPx
            overlapsEnd -> itemEndPx - viewportEndPx
            else -> 0
        }
    return StripReveal(scrollToIndex = null, scrollByPx = scrollByPx)
}
