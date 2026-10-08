package com.stratum.liftconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for the band-strip tiling math: the first band's start is
 * editable (blank reads as zero), later starts are derived from the previous
 * end, end edits re-tile neighbors, and the last band stays open-ended
 * (UNIT_TESTING.md §1 — no fakes needed, pure input).
 */
@DisplayName("IncrementBandTiling")
class IncrementBandTilingTest {

    private val twoBand =
        listOf(
            IncrementTierDraft("0", "40", "2.5", openEnded = false),
            IncrementTierDraft("40", null, "5", openEnded = true),
        )

    @Test
    @DisplayName("Given an end edit when retiled then the neighbor starts at the new end")
    fun endEditRetilesNeighbor() {
        val retiled =
            IncrementBandTiling.retileAfterEndEdit(
                tiers = twoBand,
                index = 0,
                newEnd = "100",
            )

        assertEquals("0", retiled[0].lowerBound)
        assertEquals("100", retiled[0].upperBound)
        assertEquals("100", retiled[1].lowerBound)
        assertTrue(retiled[1].openEnded)
    }

    @Test
    @DisplayName("Given an end edit on the last band when retiled then the bands are unchanged")
    fun lastBandEndEditIgnored() {
        val retiled =
            IncrementBandTiling.retileAfterEndEdit(
                tiers = twoBand,
                index = 1,
                newEnd = "200",
            )

        assertEquals(twoBand, retiled)
    }

    @Test
    @DisplayName("Given a custom first-band start when queried then stored and derived starts are distinct")
    fun storedAndDerivedStartsAreDistinct() {
        val drafts =
            listOf(
                IncrementTierDraft("10", "50", "5", openEnded = false),
                IncrementTierDraft("50", null, "10", openEnded = true),
            )

        assertEquals("10", IncrementBandTiling.firstBandStartOrZero(drafts))
        assertEquals("50", IncrementBandTiling.startAt(drafts, 1))
    }

    @Test
    @DisplayName("Given a blank first-band start when firstBandStartOrZero then it falls back to zero")
    fun firstStartFallsBackToZeroWhenBlank() {
        val drafts = listOf(IncrementTierDraft("", null, "5", openEnded = true))

        assertEquals("0", IncrementBandTiling.firstBandStartOrZero(drafts))
    }

    @Test
    @DisplayName("Given band 0 when startAt then it rejects the call because the start is stored state")
    fun startAtRejectsBandZero() {
        val drafts = listOf(IncrementTierDraft("10", "50", "5", openEnded = false))

        assertThrows<IllegalArgumentException> { IncrementBandTiling.startAt(drafts, 0) }
    }

    @Test
    @DisplayName("Given a custom first-band start when a neighbor end is edited then the start is preserved")
    fun endEditPreservesCustomFirstStart() {
        val drafts =
            listOf(
                IncrementTierDraft("10", "50", "5", openEnded = false),
                IncrementTierDraft("50", null, "10", openEnded = true),
            )

        val retiled = IncrementBandTiling.retileAfterEndEdit(drafts, 0, "60")

        assertEquals("10", retiled[0].lowerBound)
        assertEquals("60", retiled[0].upperBound)
        assertEquals("60", retiled[1].lowerBound)
        assertTrue(retiled[1].openEnded)
    }

    @Test
    @DisplayName("Given a band list when retiled then the first keeps its start and last stays open")
    fun retiledPreservesFirstStartAndOpensLast() {
        val drafts =
            listOf(
                IncrementTierDraft("7", "100", "5", openEnded = false),
                IncrementTierDraft("999", "200", "10", openEnded = false),
            )

        val retiled = IncrementBandTiling.retiled(drafts)

        assertEquals("7", retiled[0].lowerBound)
        assertEquals("100", retiled[1].lowerBound)
        assertTrue(retiled[1].openEnded)
        assertNull(retiled[1].upperBound)
        assertEquals("7", IncrementBandTiling.firstBandStartOrZero(retiled))
        assertEquals("100", IncrementBandTiling.startAt(retiled, 1))
    }
}
