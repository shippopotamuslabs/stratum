package com.stratum.core.ui.trainingsetup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ScheduleDraftUiCycleOpsTest {
    @Test
    @DisplayName("withTrainingDayCount 6 enables cycle options and Mon–Sat placeholders")
    fun trainingDayCountSix_defaultsCycle() {
        val draft =
            ScheduleDraftUi(trainingDayCount = 4)
                .withTrainingDayCount(6)
        assertEquals(6, draft.trainingDayCount)
        assertTrue(draft.showSixDayCycleOptions)
        assertTrue(draft.showRestPlacement)
        assertEquals(8, draft.cycleLength)
        assertEquals(setOf(3, 7), draft.restSlotIndices)
        assertEquals(setOf(0, 1, 2, 4, 5, 6), draft.trainingSlotIndices)
    }

    @Test
    @DisplayName("withTrainingDayCount 4 uses 7-slot week with three rests")
    fun trainingDayCountFour_sevenDayWeek() {
        val draft =
            ScheduleDraftUi(
                trainingDayCount = 6,
                cycleLength = 8,
                restSlotIndices = setOf(3, 7),
            ).withTrainingDayCount(4)
        assertEquals(4, draft.trainingDayCount)
        assertTrue(draft.showRestPlacement)
        assertFalse(draft.showSixDayCycleOptions)
        assertEquals(7, draft.cycleLength)
        assertEquals(3, draft.restSlotIndices.size)
        assertEquals(4, draft.trainingSlotIndices.size)
    }

    @Test
    @DisplayName("withTrainingDayCount 3 defaults to Day 1/3/5 train slots")
    fun trainingDayCountThree_day135() {
        val draft = ScheduleDraftUi().withTrainingDayCount(3)
        assertEquals(7, draft.cycleLength)
        assertEquals(setOf(0, 2, 4), draft.trainingSlotIndices)
        assertEquals(setOf(1, 3, 5, 6), draft.restSlotIndices)
    }

    @Test
    @DisplayName("isReadyForGeneration requires valid placement and session length")
    fun readyForGeneration_restAndSession() {
        val incomplete =
            ScheduleDraftUi(
                trainingDayCount = 4,
                cycleLength = 7,
                restSlotIndices = setOf(2, 5, 6),
                sessionLengthSelection = null,
            )
        assertFalse(incomplete.isReadyForGeneration())
        val complete = incomplete.copy(sessionLengthSelection = SESSION_BAND_KEY_MEDIUM)
        assertTrue(complete.isReadyForGeneration())
        val badRests = complete.copy(restSlotIndices = emptySet())
        assertFalse(badRests.isReadyForGeneration())
    }

    @Test
    @DisplayName("withSixDayCycleLengthSelected toggles to 7-day fixed rests")
    fun cycleLengthToggle_sevenDay() {
        val draft =
            ScheduleDraftUi(trainingDayCount = 4)
                .withTrainingDayCount(6)
                .withSixDayCycleLengthSelected(7)
        assertEquals(7, draft.cycleLength)
        assertEquals(
            ScheduleDraftUi.defaultRestSlotsForCycleLength(7, trainingDayCount = 6),
            draft.restSlotIndices,
        )
        assertEquals(6, draft.trainingSlotIndices.size)
    }

    @Test
    @DisplayName("withTrainingSlotToggled replaces the selected train day closest to the new tap")
    fun trainToggle_replacesClosestWhenFull() {
        // N=3 default train {0,2,4}. Tapping 1 is closer to 0 and 2; tie abs → lower (0).
        val afterOne =
            ScheduleDraftUi()
                .withTrainingDayCount(3)
                .withTrainingSlotToggled(1)
        assertEquals(setOf(1, 2, 4), afterOne.trainingSlotIndices)
        assertEquals(setOf(0, 3, 5, 6), afterOne.restSlotIndices)

        // From {0,2,4}, tapping 6 is closer to 4 → drop 4.
        val afterSix =
            ScheduleDraftUi()
                .withTrainingDayCount(3)
                .withTrainingSlotToggled(6)
        assertEquals(setOf(0, 2, 6), afterSix.trainingSlotIndices)
    }

    @Test
    @DisplayName("withTrainingSlotToggled for N=6 keeps six train slots on 8-day cycle")
    fun trainToggle_sixDayKeepsSixTrain() {
        val draft =
            ScheduleDraftUi()
                .withTrainingDayCount(6)
                .withTrainingSlotToggled(3) // was rest; replace closest train
        assertEquals(8, draft.cycleLength)
        assertEquals(6, draft.trainingSlotIndices.size)
        assertTrue(3 in draft.trainingSlotIndices)
        assertEquals(2, draft.restSlotIndices.size)
    }
}
