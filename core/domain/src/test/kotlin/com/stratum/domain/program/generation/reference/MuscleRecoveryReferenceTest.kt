package com.stratum.domain.program.generation.reference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MuscleRecoveryReferenceTest {

    @Test
    @DisplayName("triceps require at least 2 calendar days between meaningful sessions")
    fun tricepsMinimumGap() {
        assertEquals(2, minimumRecoveryCalendarGap("Tricep", 2.0, 8.0))
        assertEquals(2, minimumRecoveryCalendarGap("Tricep", 2.0, 2.0))
    }

    @Test
    @DisplayName("heavy quad work requires 3 calendar days before repeat")
    fun heavyQuadGap() {
        assertEquals(3, minimumRecoveryCalendarGap("Quad", 10.0, 10.0))
    }

    @Test
    @DisplayName("very heavy arm specialization prefers 3 calendar days")
    fun veryHeavyArmGap() {
        assertEquals(3, minimumRecoveryCalendarGap("Tricep", 14.0, 14.0))
    }

    @Test
    @DisplayName("light calf touch still needs 2 calendar days before direct calf work")
    fun lightCalfGap() {
        assertEquals(2, minimumRecoveryCalendarGap("Calf", 2.0, 6.0))
    }
}
