package com.stratum.liftconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for the band-strip tiling math: starts are derived (first at
 * zero, rest at the previous end), end edits re-tile neighbors, and the last
 * band stays open-ended (UNIT_TESTING.md §1 — no fakes needed, pure input).
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
    @DisplayName("Given a band list when retiled then the first starts at zero and last stays open")
    fun retiledDerivesStartsAndOpenLast() {
        val drafts =
            listOf(
                IncrementTierDraft("7", "100", "5", openEnded = false),
                IncrementTierDraft("999", "200", "10", openEnded = false),
            )

        val retiled = IncrementBandTiling.retiled(drafts)

        assertEquals("0", retiled[0].lowerBound)
        assertEquals("100", retiled[1].lowerBound)
        assertTrue(retiled[1].openEnded)
        assertNull(retiled[1].upperBound)
        assertEquals("0", IncrementBandTiling.startAt(retiled, 0))
        assertEquals("100", IncrementBandTiling.startAt(retiled, 1))
    }
}
