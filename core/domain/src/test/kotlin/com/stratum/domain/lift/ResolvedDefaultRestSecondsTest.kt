package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResolvedDefaultRestSecondsTest {

    @Test
    @DisplayName("Isolation lift without override uses 2 minute default")
    fun isolationDefault() {
        val lift = sampleLift(patterns = setOf(MovementPattern.ElbowFlexion), override = null)
        assertEquals(DEFAULT_ISOLATION_REST_SECONDS, lift.resolvedDefaultRestSeconds())
    }

    @Test
    @DisplayName("Compound lift without override uses 3 minute default")
    fun compoundDefault() {
        val lift =
            sampleLift(
                patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
                override = null,
            )
        assertEquals(DEFAULT_COMPOUND_REST_SECONDS, lift.resolvedDefaultRestSeconds())
    }

    @Test
    @DisplayName("Stored override wins over compound/isolation defaults")
    fun overrideWins() {
        val lift =
            sampleLift(
                patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
                override = 90,
            )
        assertEquals(90, lift.resolvedDefaultRestSeconds())
    }

    private fun sampleLift(
        patterns: Set<MovementPattern>,
        override: Int?,
    ): Lift =
        Lift(
            id = LiftId("lift-1"),
            name = "Sample",
            movementPatterns = patterns,
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            defaultRestSeconds = override,
        )
}
