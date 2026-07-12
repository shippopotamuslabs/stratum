package com.stratum.domain.program.generation.reference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SessionLengthBudgetTest {

    @Test
    @DisplayName("soft aim 30 expands to 20–40 band")
    fun shortBand() {
        val b = sessionLengthBudget(30)!!
        assertEquals(30, b.aimMinutes)
        assertEquals(40, b.maxMinutes)
        assertEquals(10, b.aimHardSets)
        assertEquals(13, b.maxHardSets)
        // max 40 + grace 5 = 45 → floor(45/3)=15
        assertEquals(15, b.trimHardSets)
    }

    @Test
    @DisplayName("soft aim 50 expands to 40–60 band")
    fun mediumBand() {
        val b = sessionLengthBudget(50)!!
        assertEquals(50, b.aimMinutes)
        assertEquals(60, b.maxMinutes)
    }

    @Test
    @DisplayName("soft aim 75 expands to 60–90 band")
    fun longBand() {
        val b = sessionLengthBudget(75)!!
        assertEquals(75, b.aimMinutes)
        assertEquals(90, b.maxMinutes)
        assertEquals(25, b.aimHardSets)
        assertEquals(30, b.maxHardSets)
        // 90+5=95 → floor(95/3)=31
        assertEquals(31, b.trimHardSets)
    }

    @Test
    @DisplayName("custom minutes use aim = max")
    fun custom() {
        val b = sessionLengthBudget(55)!!
        assertEquals(55, b.aimMinutes)
        assertEquals(55, b.maxMinutes)
    }

    @Test
    @DisplayName("band keys resolve to midpoints")
    fun bandKeys() {
        assertEquals(30, sessionLengthBudgetForBandKey("20-40", null)?.aimMinutes)
        assertEquals(50, sessionLengthBudgetForBandKey("40-60", null)?.aimMinutes)
        assertEquals(75, sessionLengthBudgetForBandKey("60-90", null)?.aimMinutes)
        assertEquals(88, sessionLengthBudgetForBandKey("custom", 88)?.aimMinutes)
        assertNull(sessionLengthBudgetForBandKey(null, null))
    }

    @Test
    @DisplayName("sessionHardSetCapForLength uses band max")
    fun hardCapIsMax() {
        assertEquals(30, sessionHardSetCapForLength(75))
        assertEquals(25, sessionAimHardSetsForLength(75))
    }
}
