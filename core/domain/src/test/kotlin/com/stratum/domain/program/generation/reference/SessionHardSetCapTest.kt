package com.stratum.domain.program.generation.reference

import com.stratum.domain.program.generation.context.MINUTES_PER_HARD_SET
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [sessionHardSetCapForLength] returns **band max** hard sets (soft midpoints expand).
 * Soft aim is [sessionAimHardSetsForLength].
 */
class SessionHardSetCapTest {
    @Test
    @DisplayName("band midpoints expand to max hard-set caps")
    fun bandMidpointsUseMaxCaps() {
        // 30 → max 40 → floor(40/3)=13
        assertEquals(13, sessionHardSetCapForLength(30))
        assertEquals(10, sessionAimHardSetsForLength(30))
        // 50 → max 60 → 20
        assertEquals(20, sessionHardSetCapForLength(50))
        assertEquals(16, sessionAimHardSetsForLength(50))
        // 75 → max 90 → 30
        assertEquals(30, sessionHardSetCapForLength(75))
        assertEquals(25, sessionAimHardSetsForLength(75))
    }

    @Test
    @DisplayName("non-midpoint minutes use aim = max")
    fun customMinutesAimEqualsMax() {
        assertEquals(15, sessionHardSetCapForLength(45))
        assertEquals(15, sessionAimHardSetsForLength(45))
        assertEquals(20, sessionHardSetCapForLength(60))
        assertEquals(20, sessionAimHardSetsForLength(60))
        assertEquals(80, sessionHardSetCapForLength(240))
    }

    @Test
    @DisplayName("duration budget is band max minutes")
    fun durationBudgetIsBandMax() {
        assertEquals(90, sessionDurationBudgetMinutes(75))
        assertEquals(60, sessionDurationBudgetMinutes(60))
    }

    @Test
    fun capIsNullWhenSessionLengthMissing() {
        assertNull(sessionHardSetCapForLength(null))
        assertNull(sessionHardSetCapForLength(0))
    }

    @Test
    fun estimatedMinutesAlignWithCustomCap() {
        val cap = checkNotNull(sessionHardSetCapForLength(60))
        assertEquals(60.0, cap * MINUTES_PER_HARD_SET)
    }
}
