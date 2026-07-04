package com.stratum.domain.program.generation.reference

import com.stratum.domain.program.generation.context.MINUTES_PER_HARD_SET
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionHardSetCapTest {
    @Test
    fun presetSessionLengthsMapToExpectedHardSetCaps() {
        assertEquals(10, sessionHardSetCapForLength(30))
        assertEquals(20, sessionHardSetCapForLength(60))
        assertEquals(25, sessionHardSetCapForLength(75))
    }

    @Test
    fun customSessionLengthUsesThreeMinutesPerSet() {
        assertEquals(15, sessionHardSetCapForLength(45))
        assertEquals(16, sessionHardSetCapForLength(50))
        assertEquals(80, sessionHardSetCapForLength(240))
    }

    @Test
    fun durationBudgetMatchesSessionLength() {
        assertEquals(60, sessionDurationBudgetMinutes(60))
    }

    @Test
    fun capIsNullWhenSessionLengthMissing() {
        assertNull(sessionHardSetCapForLength(null))
        assertNull(sessionHardSetCapForLength(0))
    }

    @Test
    fun estimatedMinutesAlignWithCap() {
        val cap = checkNotNull(sessionHardSetCapForLength(60))
        assertEquals(60.0, cap * MINUTES_PER_HARD_SET)
    }
}
