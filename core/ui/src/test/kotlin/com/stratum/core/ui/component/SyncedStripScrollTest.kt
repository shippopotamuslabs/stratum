package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Synced strip reveal")
class SyncedStripScrollTest {

    @Test
    @DisplayName("fully inside the viewport does not scroll")
    fun fullyInsideDoesNotScroll() {
        val inset =
            stripReveal(
                itemIndex = 1,
                itemOffsetPx = 20,
                itemSizePx = 40,
                viewportStartPx = 0,
                viewportEndPx = 100,
            )
        assertNull(inset.scrollToIndex)
        assertEquals(0, inset.scrollByPx)

        val flush =
            stripReveal(
                itemIndex = 0,
                itemOffsetPx = 0,
                itemSizePx = 100,
                viewportStartPx = 0,
                viewportEndPx = 100,
            )
        assertNull(flush.scrollToIndex)
        assertEquals(0, flush.scrollByPx)
    }

    @Test
    @DisplayName("right edge past the viewport scrolls by that overflow")
    fun rightEdgeOverflow() {
        val reveal =
            stripReveal(
                itemIndex = 3,
                itemOffsetPx = 180,
                itemSizePx = 50,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertNull(reveal.scrollToIndex)
        assertEquals(30, reveal.scrollByPx)
    }

    @Test
    @DisplayName("left edge before the viewport scrolls back by that overflow")
    fun leftEdgeOverflow() {
        val reveal =
            stripReveal(
                itemIndex = 1,
                itemOffsetPx = 10,
                itemSizePx = 50,
                viewportStartPx = 40,
                viewportEndPx = 200,
            )
        assertNull(reveal.scrollToIndex)
        assertEquals(-30, reveal.scrollByPx)
    }

    @Test
    @DisplayName("null offset or null size scrolls to the item index")
    fun nullOffsetOrSizeScrollsToIndex() {
        val missingOffset =
            stripReveal(
                itemIndex = 5,
                itemOffsetPx = null,
                itemSizePx = 80,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(5, missingOffset.scrollToIndex)
        assertEquals(0, missingOffset.scrollByPx)

        val missingSize =
            stripReveal(
                itemIndex = 2,
                itemOffsetPx = 10,
                itemSizePx = null,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(2, missingSize.scrollToIndex)
        assertEquals(0, missingSize.scrollByPx)
    }

    @Test
    @DisplayName("item wider than the viewport start-aligns when its start is inside")
    fun widerThanViewportStartAlignsWhenStartIsInside() {
        val reveal =
            stripReveal(
                itemIndex = 0,
                itemOffsetPx = 20,
                itemSizePx = 400,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertNull(reveal.scrollToIndex)
        assertEquals(20, reveal.scrollByPx)
    }

    @Test
    @DisplayName("forward off-screen target scrolls by the end overflow, not a pin to zero")
    fun forwardOffscreenScrollsByEndOverflow() {
        val delta =
            stripRevealOffscreenDelta(
                targetIndex = 2,
                anchorIndex = 1,
                anchorOffsetPx = 150,
                anchorSizePx = 40,
                spacingPx = 4,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(34, delta)
    }

    @Test
    @DisplayName("backward off-screen target start-aligns")
    fun backwardOffscreenStartAligns() {
        val delta =
            stripRevealOffscreenDelta(
                targetIndex = 1,
                anchorIndex = 3,
                anchorOffsetPx = 40,
                anchorSizePx = 50,
                spacingPx = 10,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(-80, delta)
    }
}
