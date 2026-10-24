package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Lift navigator scroll + grouping helpers")
class LiftNavigatorScrollTest {

    @Test
    @DisplayName("selects the lift whose top is closest to the viewport threshold")
    fun closestTopEdgeWins() {
        val liftKeys = setOf("a", "b", "c")
        // a is above the fold (offset -80), b is just below top (offset 40) → b is closer to 0
        val selected =
            primaryLiftIdFromScroll(
                visibleItems = listOf("a" to -80, "b" to 40, "c" to 400),
                liftKeys = liftKeys,
                threshold = 0,
            )
        assertEquals("b", selected)
    }

    @Test
    @DisplayName("prefers a slightly-past-threshold lift over one still far below")
    fun slightlyPastThresholdPreferred() {
        val selected =
            primaryLiftIdFromScroll(
                visibleItems = listOf("rdl" to 120, "superset" to -20),
                liftKeys = setOf("rdl", "superset"),
                threshold = 0,
            )
        assertEquals("superset", selected)
    }

    @Test
    @DisplayName("ignores non-lift list keys such as session footer / spacer")
    fun ignoresNonLiftKeys() {
        val selected =
            primaryLiftIdFromScroll(
                visibleItems = listOf(WORKOUT_SESSION_FOOTER_KEY to 0, "wl-1" to 10),
                liftKeys = setOf("wl-1"),
                threshold = 0,
            )
        assertEquals("wl-1", selected)
    }

    @Test
    @DisplayName("returns null when no lift items are visible")
    fun nullWhenEmpty() {
        assertNull(
            primaryLiftIdFromScroll(
                visibleItems = listOf(WORKOUT_SESSION_FOOTER_KEY to 0),
                liftKeys = setOf("wl-1"),
            ),
        )
    }

    @Test
    @DisplayName("scroll delta down sums heights and spacings between from and to")
    fun scrollDeltaDown() {
        val heights = listOf(100, 200, 300, 400)
        val delta =
            scrollDeltaToItemTopPx(
                fromIndex = 1,
                fromItemOffset = 0,
                toIndex = 3,
                itemHeightPx = { heights[it] },
                spacingPx = 10,
            )
        // h1 + sp + h2 + sp = 200+10+300+10
        assertEquals(520, delta)
    }

    @Test
    @DisplayName("scroll delta up is negative and accounts for current item offset")
    fun scrollDeltaUp() {
        val heights = listOf(100, 200, 300, 400)
        val delta =
            scrollDeltaToItemTopPx(
                fromIndex = 3,
                fromItemOffset = -20,
                toIndex = 0,
                itemHeightPx = { heights[it] },
                spacingPx = 10,
            )
        // -(h0+sp+h1+sp+h2+sp) + offset = -(100+10+200+10+300+10) - 20
        assertEquals(-(100 + 10 + 200 + 10 + 300 + 10) - 20, delta)
    }

    @Test
    @DisplayName("scroll delta same index returns current offset")
    fun scrollDeltaSameIndex() {
        assertEquals(
            40,
            scrollDeltaToItemTopPx(
                fromIndex = 2,
                fromItemOffset = 40,
                toIndex = 2,
                itemHeightPx = { 100 },
                spacingPx = 8,
            ),
        )
    }

    @Test
    @DisplayName("scroll delta with pin offset lands the target at the pin line, not zero")
    fun scrollDeltaWithPinOffset() {
        val heights = listOf(100, 200, 300, 400)
        // Same index: scroll up by exactly the pin offset.
        assertEquals(
            -60,
            scrollDeltaToItemTopPx(
                fromIndex = 2,
                fromItemOffset = 40,
                toIndex = 2,
                itemHeightPx = { 100 },
                spacingPx = 8,
                pinOffsetPx = 100,
            ),
        )
        // Down: content distance minus the pin offset.
        assertEquals(
            520 - 100,
            scrollDeltaToItemTopPx(
                fromIndex = 1,
                fromItemOffset = 0,
                toIndex = 3,
                itemHeightPx = { heights[it] },
                spacingPx = 10,
                pinOffsetPx = 100,
            ),
        )
        // Up: content distance minus the pin offset, from a physical offset.
        assertEquals(
            -(100 + 10 + 200 + 10 + 300 + 10) - 20 - 100,
            scrollDeltaToItemTopPx(
                fromIndex = 3,
                fromItemOffset = -20,
                toIndex = 0,
                itemHeightPx = { heights[it] },
                spacingPx = 10,
                pinOffsetPx = 100,
            ),
        )
    }

    @Test
    @DisplayName("pin threshold selects the lift closest to the overlay pin line")
    fun pinThresholdUnderOverlay() {
        val liftKeys = setOf("a", "b")
        // Top padding 100: at rest the first lift sits at offset 100 == pin line.
        assertEquals(
            "a",
            primaryLiftIdFromScroll(
                visibleItems = listOf("a" to 100, "b" to 500),
                liftKeys = liftKeys,
                threshold = 100,
            ),
        )
        // Scrolled 60px: "a" (40) is past the line, "b" (440) far below → "a" still wins.
        assertEquals(
            "a",
            primaryLiftIdFromScroll(
                visibleItems = listOf("a" to 40, "b" to 440),
                liftKeys = liftKeys,
                threshold = 100,
            ),
        )
    }

    @Test
    @DisplayName("bottom pin padding equals viewport minus last lift, footer, and one gap")
    fun bottomPinSpacerSizing() {
        assertEquals(
            300,
            bottomPinSpacerHeightPx(
                viewportHeightPx = 1000,
                lastLiftHeightPx = 400,
                footerHeightPx = 200,
                listSpacingPx = 100,
            ),
        )
        assertEquals(
            0,
            bottomPinSpacerHeightPx(
                viewportHeightPx = 500,
                lastLiftHeightPx = 400,
                footerHeightPx = 200,
                listSpacingPx = 16,
            ),
        )
        assertEquals(
            0,
            bottomPinSpacerHeightPx(
                viewportHeightPx = 1000,
                lastLiftHeightPx = 0,
                footerHeightPx = 100,
                listSpacingPx = 16,
            ),
        )
    }

    @Test
    @DisplayName("bottom pin spacer shrinks by the top content padding under an overlay")
    fun bottomPinSpacerWithTopPadding() {
        // Top padding steals pin travel: max scroll otherwise lands the last lift
        // at the physical viewport top (under the overlay).
        assertEquals(
            200,
            bottomPinSpacerHeightPx(
                viewportHeightPx = 1000,
                lastLiftHeightPx = 400,
                footerHeightPx = 200,
                listSpacingPx = 100,
                topContentPaddingPx = 100,
            ),
        )
        assertEquals(
            0,
            bottomPinSpacerHeightPx(
                viewportHeightPx = 500,
                lastLiftHeightPx = 400,
                footerHeightPx = 200,
                listSpacingPx = 16,
                topContentPaddingPx = 300,
            ),
        )
    }

    @Test
    @DisplayName("groups consecutive same-superset lifts together")
    fun groupsSupersets() {
        val lifts =
            listOf(
                lift("a", null),
                lift("b", "g1"),
                lift("c", "g1"),
                lift("d", null),
                lift("e", "g2"),
                lift("f", "g2"),
                lift("g", "g2"),
            )
        val groups = groupLiftsForNavigator(lifts)
        assertEquals(
            listOf(
                listOf("a"),
                listOf("b", "c"),
                listOf("d"),
                listOf("e", "f", "g"),
            ),
            groups.map { group -> group.map { it.workoutLiftId } },
        )
    }

    private fun lift(id: String, group: String?) =
        RenderedLiftUi(
            workoutLiftId = id,
            liftId = id,
            liftName = id,
            sets = emptyList(),
            supersetGroupId = group,
        )
}
