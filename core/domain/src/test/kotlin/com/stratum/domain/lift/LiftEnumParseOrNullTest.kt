package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LiftEnumParseOrNullTest {
    @Test
    fun `FatigueProfile parseOrNull returns the exact-name entry`() {
        assertEquals(FatigueProfile.High, FatigueProfile.parseOrNull("High"))
        assertEquals(FatigueProfile.Medium, FatigueProfile.parseOrNull("Medium"))
        assertEquals(FatigueProfile.Low, FatigueProfile.parseOrNull("Low"))
    }

    @Test
    fun `FatigueProfile parseOrNull returns null for non-exact names`() {
        assertNull(FatigueProfile.parseOrNull("high"))
        assertNull(FatigueProfile.parseOrNull("AxialCompound"))
        assertNull(FatigueProfile.parseOrNull(""))
    }

    @Test
    fun `MovementPattern parseOrNull returns the exact-name entry`() {
        assertEquals(MovementPattern.SquatPattern, MovementPattern.parseOrNull("SquatPattern"))
        assertEquals(MovementPattern.HorizontalPress, MovementPattern.parseOrNull("HorizontalPress"))
        assertEquals(MovementPattern.HipAdduction, MovementPattern.parseOrNull("HipAdduction"))
    }

    @Test
    fun `MovementPattern parseOrNull returns null for non-exact names`() {
        assertNull(MovementPattern.parseOrNull("squatPattern"))
        assertNull(MovementPattern.parseOrNull("Squat"))
        assertNull(MovementPattern.parseOrNull(""))
    }
}
