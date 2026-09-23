package com.stratum.feature.workout

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EquipmentAdjustTracker")
class EquipmentAdjustTrackerTest {

    private val lb = Weight.WeightUnit.Lb
    private val wl1 = WorkoutLiftId("wl-1")
    private val tiersA = listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    private val tiersB = listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb)))

    @Test
    @DisplayName("Given first sighting when updated then the card stays visible and snapshots tiers")
    fun firstSightingStaysVisible() {
        val tracker = EquipmentAdjustTracker()

        val visible =
            tracker.update(
                rawIds = setOf(wl1),
                tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
                presentIds = setOf(wl1),
            )

        assertEquals(setOf(wl1), visible)
    }

    @Test
    @DisplayName("Given unchanged tiers when updated then the card stays visible")
    fun unchangedTiersStayVisible() {
        val tracker = EquipmentAdjustTracker()
        tracker.update(
            rawIds = setOf(wl1),
            tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
            presentIds = setOf(wl1),
        )

        val visible =
            tracker.update(
                rawIds = setOf(wl1),
                tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
                presentIds = setOf(wl1),
            )

        assertEquals(setOf(wl1), visible)
    }

    @Test
    @DisplayName("Given edited tiers when updated then the card resolves quietly")
    fun editedTiersResolveQuietly() {
        val tracker = EquipmentAdjustTracker()
        tracker.update(
            rawIds = setOf(wl1),
            tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
            presentIds = setOf(wl1),
        )

        val visible =
            tracker.update(
                rawIds = setOf(wl1),
                tiersByWorkoutLiftId = mapOf(wl1 to tiersB),
                presentIds = setOf(wl1),
            )

        assertTrue(visible.isEmpty())
    }

    @Test
    @DisplayName("Given dismiss when updated then the card stays quiet")
    fun dismissStaysQuiet() {
        val tracker = EquipmentAdjustTracker()
        tracker.update(
            rawIds = setOf(wl1),
            tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
            presentIds = setOf(wl1),
        )
        tracker.dismiss(wl1)

        val visible =
            tracker.update(
                rawIds = setOf(wl1),
                tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
                presentIds = setOf(wl1),
            )

        assertTrue(visible.isEmpty())
    }

    @Test
    @DisplayName("Given departed cards when updated then bookkeeping is pruned")
    fun departedCardsPruned() {
        val tracker = EquipmentAdjustTracker()
        tracker.update(
            rawIds = setOf(wl1),
            tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
            presentIds = setOf(wl1),
        )
        tracker.dismiss(wl1)

        // Card left the session: re-adding the same id starts fresh, not consumed.
        val visible =
            tracker.update(
                rawIds = emptySet(),
                tiersByWorkoutLiftId = emptyMap(),
                presentIds = emptySet(),
            )

        assertTrue(visible.isEmpty())
        val fresh =
            tracker.update(
                rawIds = setOf(wl1),
                tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
                presentIds = setOf(wl1),
            )
        assertEquals(setOf(wl1), fresh)
    }

    @Test
    @DisplayName("Given clear when updated then consumed cards show again")
    fun clearResetsSession() {
        val tracker = EquipmentAdjustTracker()
        tracker.update(
            rawIds = setOf(wl1),
            tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
            presentIds = setOf(wl1),
        )
        tracker.dismiss(wl1)
        tracker.clear()

        val visible =
            tracker.update(
                rawIds = setOf(wl1),
                tiersByWorkoutLiftId = mapOf(wl1 to tiersA),
                presentIds = setOf(wl1),
            )

        assertEquals(setOf(wl1), visible)
    }
}
