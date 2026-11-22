package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Rest ring fill fraction (STR-329). */
class RestCountdownRingTest {
    @Test
    fun fractionIsRemainingOverTotal() {
        assertEquals(0.5f, restRemainingFraction(remainingSeconds = 45, totalSeconds = 90))
    }

    @Test
    fun fullRestReadsAsFullRing() {
        assertEquals(1f, restRemainingFraction(remainingSeconds = 90, totalSeconds = 90))
    }

    @Test
    fun elapsedRestReadsAsEmptyRing() {
        assertEquals(0f, restRemainingFraction(remainingSeconds = 0, totalSeconds = 90))
    }

    @Test
    fun remainingPastTotalClampsToFullRing() {
        assertEquals(1f, restRemainingFraction(remainingSeconds = 100, totalSeconds = 90))
    }

    @Test
    fun zeroTotalReadsAsEmptyRing() {
        assertEquals(0f, restRemainingFraction(remainingSeconds = 30, totalSeconds = 0))
    }
}
