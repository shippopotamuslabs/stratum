package com.stratum.domain.program.generation.integration

import com.stratum.core.testing.program.GenerationContextTestFixtures
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

private fun pressLift(id: String): LiftContext =
    LiftContext(
        id = id,
        name = id,
        movementPattern = MovementPattern.HorizontalPress,
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = setOf(MuscleGroup.Tricep),
    )

class LiftAlternativesTest {
    @Test
    @DisplayName("Unknown current returns enabled known seeds capped to the alternative limit")
    fun `unknown current filters and caps seeds`() {
        val presses = listOf("bench") + (1..11).map { "press-$it" }
        val base =
            GenerationContextTestFixtures.contextWith(
                provenLifts = presses.map(::pressLift),
                availableLifts =
                    listOf(
                        LiftContext(
                            id = "disabled-press",
                            name = "disabled-press",
                            movementPattern = MovementPattern.HorizontalPress,
                            movementPatterns = setOf(MovementPattern.HorizontalPress),
                            primaryMuscles = setOf(MuscleGroup.Chest),
                            secondaryMuscles = setOf(MuscleGroup.Tricep),
                        ),
                    ),
            )
        val context = base.copy(enabledLiftIds = base.enabledLiftIds - "disabled-press")
        val seeds = presses + listOf("ghost-id", "disabled-press")

        val result = alternativesForLift(context, "missing-lift", seeds)

        assertEquals(LIFT_ALTERNATIVES_PER_LIFT, result.size)
        assertFalse("ghost-id" in result)
        assertFalse("disabled-press" in result)
        assertTrue(result.all { it in context.enabledLiftIds })
    }

    @Test
    @DisplayName("Known current ranks pattern and primary matched lifts only")
    fun `known current returns pattern matched alternatives`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                provenLifts =
                    listOf(
                        pressLift("bench"),
                        pressLift("db-press"),
                        LiftContext(
                            id = "row",
                            name = "row",
                            movementPattern = MovementPattern.HorizontalPull,
                            movementPatterns = setOf(MovementPattern.HorizontalPull),
                            primaryMuscles = setOf(MuscleGroup.Back),
                            secondaryMuscles = setOf(MuscleGroup.Bicep),
                        ),
                    ),
            )

        val result = alternativesForLift(context, "bench", emptyList())

        assertTrue("db-press" in result)
        assertFalse("row" in result)
    }

    @Test
    @DisplayName("Alternative ordering is deterministic across calls")
    fun `ordering is stable`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                provenLifts = listOf("bench", "db-press", "inc-press").map(::pressLift),
            )

        assertEquals(
            alternativesForLift(context, "bench", listOf("db-press")),
            alternativesForLift(context, "bench", listOf("db-press")),
        )
    }
}
