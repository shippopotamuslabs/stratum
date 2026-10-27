package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EquipmentBehaviorTest {

    private val lb = Weight.WeightUnit.Lb
    private val stops = listOf(Weight(5.0, lb), Weight(10.0, lb))

    private fun equipment(
        tiers: List<IncrementTier> = emptyList(),
        micro: List<Weight> = stops,
        isDeleted: Boolean = false,
    ) = Equipment(
        id = EquipmentId("eq-1"),
        name = "Stack",
        incrementTiers = tiers,
        microIncrements = micro,
        isDeleted = isDeleted,
    )

    @Test
    @DisplayName("Empty tiers mean the default single band; stored tiers win")
    fun effectiveTiers() {
        assertEquals(defaultSingleTiers(), equipment().effectiveTiers())
        val stored = listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb)))
        assertEquals(stored, equipment(tiers = stored).effectiveTiers())
    }

    @Test
    @DisplayName("Fork micro: null inherits the parent stops, a list replaces them")
    fun forkMicroSemantics() {
        assertEquals(stops, equipment().resolvedForkMicro(null))
        assertEquals(emptyList<Weight>(), equipment().resolvedForkMicro(emptyList()))
        val replacement = listOf(Weight(2.5, lb))
        assertEquals(replacement, equipment().resolvedForkMicro(replacement))
    }

    @Test
    @DisplayName("Tombstone meaning is named on both Equipment and Lift")
    fun tombstoneSemantics() {
        assertTrue(equipment(isDeleted = true).isTombstoned)
        assertFalse(equipment(isDeleted = true).isLive)
        assertTrue(equipment().isLive)

        val lift =
            Lift(
                id = LiftId("lift-1"),
                name = "Press",
                movementPatterns = setOf(MovementPattern.HorizontalPress),
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                isDeleted = true,
            )
        assertTrue(lift.isTombstoned)
        assertFalse(lift.isLive)
    }
}
