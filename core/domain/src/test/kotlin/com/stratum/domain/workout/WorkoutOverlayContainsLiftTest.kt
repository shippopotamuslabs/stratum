package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutOverlayContainsLiftTest {

    private val liftId = LiftId("lift-a")
    private val otherId = LiftId("lift-b")
    private val slotId = WorkoutLiftId("wl-1")

    @Test
    @DisplayName("Swap target counts as contained")
    fun swapTargetContained() {
        val overlay = WorkoutOverlay(swappedLifts = mapOf(slotId to liftId))

        assertTrue(overlay.containsLift(liftId))
        assertFalse(overlay.containsLift(otherId))
    }

    @Test
    @DisplayName("Mid-session added lift counts as contained")
    fun addedLiftContained() {
        val overlay = WorkoutOverlay(addedLifts = listOf(OverlayAddedLift(slotId, liftId)))

        assertTrue(overlay.containsLift(liftId))
    }

    @Test
    @DisplayName("Swap source and empty overlay do not count as contained")
    fun swapSourceNotContained() {
        // Swapping lift-a OUT for lift-b: the source is gone from the session.
        val overlay = WorkoutOverlay(swappedLifts = mapOf(slotId to otherId))

        assertFalse(overlay.containsLift(liftId))
        assertFalse(WorkoutOverlay().containsLift(liftId))
    }
}
