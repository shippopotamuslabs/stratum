package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Synced strip reveal")
class StripRevealTest {

    @Test
    @DisplayName("fully inside the viewport does not scroll")
    fun fullyInsideDoesNotScroll() {
        val inset =
            stripReveal(
                itemOffsetPx = 20,
                itemSizePx = 40,
                viewportStartPx = 0,
                viewportEndPx = 100,
            )
        assertEquals(0, inset)

        val flush =
            stripReveal(
                itemOffsetPx = 0,
                itemSizePx = 100,
                viewportStartPx = 0,
                viewportEndPx = 100,
            )
        assertEquals(0, flush)
    }

    @Test
    @DisplayName("right edge past the viewport scrolls by that overflow")
    fun rightEdgeOverflow() {
        val reveal =
            stripReveal(
                itemOffsetPx = 180,
                itemSizePx = 50,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(30, reveal)
    }

    @Test
    @DisplayName("left edge before the viewport scrolls back by that overflow")
    fun leftEdgeOverflow() {
        val reveal =
            stripReveal(
                itemOffsetPx = 10,
                itemSizePx = 50,
                viewportStartPx = 40,
                viewportEndPx = 200,
            )
        assertEquals(-30, reveal)
    }

    @Test
    @DisplayName("item wider than the viewport start-aligns when its start is inside")
    fun widerThanViewportStartAlignsWhenStartIsInside() {
        val reveal =
            stripReveal(
                itemOffsetPx = 20,
                itemSizePx = 400,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(20, reveal)
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
                stepSizePx = 40,
                spacingPx = 4,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(34, delta)
    }

    @Test
    @DisplayName("forward estimate uses the narrow step, not a wide anchor, as the pitch")
    fun forwardOffscreenUsesNarrowStep() {
        // Anchor end sits on the viewport end (offset 56 + size 144). Two later
        // 70px tabs are 148px of overflow, not (144 + 4) * 2.
        val delta =
            stripRevealOffscreenDelta(
                targetIndex = 4,
                anchorIndex = 2,
                anchorOffsetPx = 56,
                anchorSizePx = 144,
                stepSizePx = 70,
                spacingPx = 4,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(148, delta)
    }

    @Test
    @DisplayName("forward landing pulls an overshot item back to the trailing edge")
    fun forwardLandingCorrectsOvershoot() {
        val delta =
            stripRevealForwardLandingDelta(
                itemOffsetPx = 10,
                itemSizePx = 40,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(-150, delta)
    }

    @Test
    @DisplayName("forward landing scrolls a trailing clip by that overflow")
    fun forwardLandingTrailingClip() {
        val delta =
            stripRevealForwardLandingDelta(
                itemOffsetPx = 180,
                itemSizePx = 40,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(20, delta)
    }

    @Test
    @DisplayName("forward landing start-aligns an item wider than the viewport")
    fun forwardLandingWideItemStartAligns() {
        val delta =
            stripRevealForwardLandingDelta(
                itemOffsetPx = 20,
                itemSizePx = 400,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(20, delta)
    }

    @Test
    @DisplayName("an overshot forward target end-aligns instead of pinning to the start")
    fun overshotForwardTargetEndAligns() {
        val delta =
            stripRevealOvershootDelta(
                targetIndex = 2,
                anchorIndex = 3,
                anchorOffsetPx = 180,
                stepSizePx = 40,
                spacingPx = 4,
                viewportEndPx = 200,
            )
        assertEquals(-24, delta)
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
                stepSizePx = 50,
                spacingPx = 10,
                viewportStartPx = 0,
                viewportEndPx = 200,
            )
        assertEquals(-80, delta)
    }
}
