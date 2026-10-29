package com.stratum.domain.checkin

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.ScoredLift

class CheckInSwapSelectionTest {

    @Test
    @DisplayName("E1: picks highest in-band similarity")
    fun picksHighestInBand() {
        val ranked =
            listOf(
                scored("a", 0.9),
                scored("b", 0.6),
                scored("c", 0.3),
            )
        assertEquals(LiftId("a"), selectSwapCandidate(ranked, emptySet())?.lift?.id)
    }

    @Test
    @DisplayName("E2: skips above MAX, picks next in band")
    fun skipsAboveMax() {
        val ranked =
            listOf(
                scored("clone", 0.97),
                scored("good", 0.6),
            )
        assertEquals(LiftId("good"), selectSwapCandidate(ranked, emptySet())?.lift?.id)
    }

    @Test
    @DisplayName("E3: only below MIN → null")
    fun belowMinNull() {
        assertNull(selectSwapCandidate(listOf(scored("weak", 0.3)), emptySet()))
    }

    @Test
    @DisplayName("E4: best in band excluded → next in band or null")
    fun excludedBest() {
        val ranked =
            listOf(
                scored("best", 0.8),
                scored("next", 0.55),
            )
        assertEquals(
            LiftId("next"),
            selectSwapCandidate(ranked, setOf(LiftId("best")))?.lift?.id,
        )
        assertNull(selectSwapCandidate(ranked, setOf(LiftId("best"), LiftId("next"))))
    }

    @Test
    @DisplayName("band edges inclusive")
    fun bandEdgesInclusive() {
        assertEquals(
            LiftId("min"),
            selectSwapCandidate(listOf(scored("min", MIN_CHECKIN_SWAP_SIMILARITY)), emptySet())
                ?.lift?.id,
        )
        assertEquals(
            LiftId("max"),
            selectSwapCandidate(listOf(scored("max", MAX_CHECKIN_SWAP_SIMILARITY)), emptySet())
                ?.lift?.id,
        )
    }

    private fun scored(id: String, similarity: Double): ScoredLift =
        ScoredLift(
            lift =
                Lift(
                    id = LiftId(id),
                    name = id,
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    secondaryMuscles = emptySet(),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = true,
                    fatigueProfile = FatigueProfile.Medium,
                ),
            similarity = similarity,
        )
}
