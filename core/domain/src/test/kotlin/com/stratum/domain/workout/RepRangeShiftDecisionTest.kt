package com.stratum.domain.workout

import com.stratum.domain.workout.fatigue.REP_RANGE_CEILING
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.identity.WorkoutLiftId

@DisplayName("evaluateRepRangeShift")
class RepRangeShiftDecisionTest {

    @Test
    @DisplayName("null slope → no proposal")
    fun nullSlopeNoProposal() {
        assertNull(
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = null,
                    setCount = 4,
                    currentRepMin = 8,
                    currentRepMax = 10,
                ),
            ),
        )
    }

    @Test
    @DisplayName("single-set lift → no proposal")
    fun singleSetNoProposal() {
        assertNull(
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -3.0,
                    setCount = 1,
                    currentRepMin = 8,
                    currentRepMax = 10,
                ),
            ),
        )
    }

    @Test
    @DisplayName("shallow slope keeps last set at floor → no proposal")
    fun shallowSlopeAtFloor() {
        assertNull(
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -1.0,
                    setCount = 4,
                    currentRepMin = 8,
                    currentRepMax = 10,
                ),
            ),
        )
    }

    @Test
    @DisplayName("steep slope raises band and preserves width")
    fun steepSlopeRaisesBandPreservesWidth() {
        val result =
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -3.0,
                    setCount = 4,
                    currentRepMin = 8,
                    currentRepMax = 10,
                ),
            )
        assertNotNull(result)
        assertEquals(14, result!!.proposedRepMin)
        assertEquals(16, result.proposedRepMax)
        assertEquals(9, result.fatigueDrop)
        assertEquals(6, result.lastSetShortfall)
    }

    @Test
    @DisplayName("fixed-rep band 8–8 preserves zero width")
    fun fixedRepPreservesZeroWidth() {
        val result =
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -3.0,
                    setCount = 4,
                    currentRepMin = 8,
                    currentRepMax = 8,
                ),
            )
        assertNotNull(result)
        assertEquals(14, result!!.proposedRepMin)
        assertEquals(14, result.proposedRepMax)
    }

    @Test
    @DisplayName("population-like slope on 8–12 raises floor")
    fun populationLikeSlope() {
        val result =
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -2.0,
                    setCount = 4,
                    currentRepMin = 8,
                    currentRepMax = 12,
                ),
            )
        assertNotNull(result)
        assertEquals(11, result!!.proposedRepMin)
        assertEquals(15, result.proposedRepMax)
    }

    @Test
    @DisplayName("proposed max is capped at REP_RANGE_CEILING")
    fun ceilingCap() {
        // drop = 4 * 9 = 36 → proposedMin = 41 > ceiling → silence
        assertNull(
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -4.0,
                    setCount = 10,
                    currentRepMin = 8,
                    currentRepMax = 12,
                ),
            ),
        )
        // just under ceiling with width that would exceed: min 25 width 10 → max 30
        val result =
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -4.0,
                    setCount = 6, // drop 20 → min 25, width 4 → 25–29
                    currentRepMin = 8,
                    currentRepMax = 12,
                ),
            )
        assertNotNull(result)
        assertEquals(25, result!!.proposedRepMin)
        assertEquals(29, result.proposedRepMax)
        assertEquals(true, result.proposedRepMax <= REP_RANGE_CEILING)
    }

    @Test
    @DisplayName("strength band 5–7 only raises when last-set math demands it")
    fun strengthBandNotForcedToHypertrophicFloor() {
        val mild =
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -0.5,
                    setCount = 3,
                    currentRepMin = 5,
                    currentRepMax = 7,
                ),
            )
        assertNotNull(mild)
        assertEquals(6, mild!!.proposedRepMin)
        assertEquals(8, mild.proposedRepMax)

        assertNull(
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -0.2,
                    setCount = 3,
                    currentRepMin = 5,
                    currentRepMax = 7,
                ),
            ),
        )
    }

    @Test
    @DisplayName("zero / empty band → no proposal")
    fun emptyBandNoProposal() {
        assertNull(
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = -3.0,
                    setCount = 4,
                    currentRepMin = 0,
                    currentRepMax = 0,
                ),
            ),
        )
    }

    @Test
    @DisplayName("fingerprint is stable for same bands")
    fun fingerprintStable() {
        val id =
            repRangeShiftProposalFingerprint(
                workoutLiftId = com.stratum.domain.program.identity.WorkoutLiftId("wl-1"),
                currentRepMin = 8,
                currentRepMax = 10,
                proposedRepMin = 14,
                proposedRepMax = 16,
            )
        assertEquals("rrs:wl-1:8-10->14-16", id)
    }
}
