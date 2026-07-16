package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Rest matrix + [Lift.isCompound] gate for fatigue-profile rename (FPR-S1-*).
 * Rest: High → 180, Medium/Low → 120, override wins; never via isCompound.
 */
class ResolvedDefaultRestSecondsTest {

    @Test
    @DisplayName("FPR-S1-001 High fatigue without override uses 180 seconds")
    fun fprS1001_highRest180() {
        val lift = fixLiftHigh()
        assertEquals(180, lift.resolvedDefaultRestSeconds())
    }

    @Test
    @DisplayName("FPR-S1-002 Medium fatigue without override uses 120 seconds")
    fun fprS1002_mediumRest120() {
        val lift = fixLiftMedium()
        assertEquals(120, lift.resolvedDefaultRestSeconds())
    }

    @Test
    @DisplayName("FPR-S1-003 Low fatigue without override uses 120 seconds")
    fun fprS1003_lowRest120() {
        val lift = fixLiftLow()
        assertEquals(120, lift.resolvedDefaultRestSeconds())
    }

    @Test
    @DisplayName("FPR-S1-004 stored override wins over fatigue defaults")
    fun fprS1004_overrideWins() {
        val highOverride =
            fixLiftHigh().copy(id = LiftId("lift-override-90"), defaultRestSeconds = 90)
        assertEquals(90, highOverride.resolvedDefaultRestSeconds())

        val mediumOverride =
            fixLiftMedium().copy(id = LiftId("lift-override-240"), defaultRestSeconds = 240)
        assertEquals(240, mediumOverride.resolvedDefaultRestSeconds())
    }

    @Test
    @DisplayName("FPR-S1-005 multi-pattern Low uses 120 not 180")
    fun fprS1005_multiPatternLowRest120() {
        val lift = fixLiftLowMulti()
        assertTrue(lift.isCompound)
        assertEquals(120, lift.resolvedDefaultRestSeconds())
        assertNotEquals(180, lift.resolvedDefaultRestSeconds())
    }

    @Test
    @DisplayName("FPR-S1-006 High single-pattern is compound")
    fun fprS1006_highIsCompound() {
        val lift = fixLiftHigh()
        assertEquals(1, lift.movementPatterns.size)
        assertTrue(lift.isCompound)
    }

    @Test
    @DisplayName("FPR-S1-007 Medium single-pattern is compound")
    fun fprS1007_mediumIsCompound() {
        val lift = fixLiftMedium()
        assertEquals(1, lift.movementPatterns.size)
        assertTrue(lift.isCompound)
    }

    @Test
    @DisplayName("FPR-S1-008 multi-pattern Low is compound")
    fun fprS1008_multiPatternLowIsCompound() {
        val lift = fixLiftLowMulti()
        assertEquals(2, lift.movementPatterns.size)
        assertTrue(lift.isCompound)
    }

    @Test
    @DisplayName("FPR-S1-009 single-pattern Low is not compound")
    fun fprS1009_singlePatternLowNotCompound() {
        val lift = fixLiftLow()
        assertEquals(1, lift.movementPatterns.size)
        assertFalse(lift.isCompound)
    }

    @Test
    @DisplayName("FPR-S1-M001 Medium single-pattern rest is 120 not 180")
    fun fprS1M001_mediumRestNotCompoundRest() {
        val lift = fixLiftMedium()
        assertTrue(lift.isCompound)
        assertEquals(120, lift.resolvedDefaultRestSeconds())
        assertNotEquals(180, lift.resolvedDefaultRestSeconds())
    }

    // --- FIX-* helpers (test_spec fixture conventions) ---

    /** FIX-LIFT-HIGH */
    private fun fixLiftHigh(): Lift =
        sampleLift(
            id = "lift-high",
            patterns = setOf(MovementPattern.SquatPattern),
            primary = setOf(MuscleGroup.Quad),
            fatigue = FatigueProfile.High,
            override = null,
        )

    /** FIX-LIFT-MEDIUM */
    private fun fixLiftMedium(): Lift =
        sampleLift(
            id = "lift-medium",
            patterns = setOf(MovementPattern.HorizontalPress),
            primary = setOf(MuscleGroup.Chest),
            fatigue = FatigueProfile.Medium,
            override = null,
        )

    /** FIX-LIFT-LOW */
    private fun fixLiftLow(): Lift =
        sampleLift(
            id = "lift-low",
            patterns = setOf(MovementPattern.ElbowFlexion),
            primary = setOf(MuscleGroup.Bicep),
            fatigue = FatigueProfile.Low,
            override = null,
        )

    /** FIX-LIFT-LOW-MULTI */
    private fun fixLiftLowMulti(): Lift =
        sampleLift(
            id = "lift-low-multi",
            patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
            primary = setOf(MuscleGroup.Chest),
            fatigue = FatigueProfile.Low,
            override = null,
        )

    private fun sampleLift(
        id: String,
        patterns: Set<MovementPattern>,
        primary: Set<MuscleGroup>,
        fatigue: FatigueProfile,
        override: Int?,
    ): Lift =
        Lift(
            id = LiftId(id),
            name = "Sample",
            movementPatterns = patterns,
            primaryMuscles = primary,
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            fatigueProfile = fatigue,
            defaultRestSeconds = override,
        )
}
