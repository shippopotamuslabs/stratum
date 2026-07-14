package com.stratum.domain.program.schedule

import com.stratum.core.testing.program.GenerationContextTestFixtures
import com.stratum.domain.program.generation.context.resolveTrainingTimeline
import com.stratum.domain.program.generation.context.withResolvedCycleSpace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TrainingTimelineTest {
    @Test
    @DisplayName("gap is forward modular distance on the ring")
    fun gapModular() {
        val timeline =
            TrainingTimeline(cycleLength = 7, trainingSlotIndices = listOf(0, 2, 4))
        assertEquals(2, timeline.gap(0, 2))
        assertEquals(3, timeline.gap(4, 0))
    }

    @Test
    @DisplayName("N=3 classic defaults: train 0,2,4 on 7-ring")
    fun classicThreeDayDefaults() {
        val timeline = TrainingTimeline.resolve(trainingSlotCount = 3)
        assertEquals(7, timeline.cycleLength)
        assertEquals(listOf(0, 2, 4), timeline.trainingSlotIndices)
        assertTrue(timeline.isRestSandwiched(0))
        assertTrue(timeline.isRestSandwiched(2))
        assertTrue(timeline.isRestSandwiched(4))
        assertTrue(timeline.adjacentTrainingPairs().isEmpty())
        assertTrue(timeline.pairsForRecoveryCheck().contains(4 to 0))
        assertEquals(3, timeline.gap(4, 0))
    }

    @Test
    @DisplayName("train-only: last→first wrap gap is 1")
    fun trainOnlyWrap() {
        val timeline =
            TrainingTimeline(cycleLength = 3, trainingSlotIndices = listOf(0, 1, 2))
        assertEquals(1, timeline.gap(2, 0))
        assertTrue(timeline.adjacentTrainingPairs().contains(2 to 0))
    }

    @Test
    @DisplayName("6-in-8 train slots wrap last→first with gap 2 (rest@7 between)")
    fun sixInEightWrap() {
        val timeline =
            TrainingTimeline(
                cycleLength = 8,
                trainingSlotIndices = listOf(0, 1, 2, 4, 5, 6),
            )
        // Train@6 → rest@7 → train@0: modular gap is 2, not adjacent.
        assertEquals(2, timeline.gap(6, 0))
        assertTrue(timeline.pairsForRecoveryCheck().contains(6 to 0))
        assertTrue(timeline.adjacentTrainingPairs().contains(0 to 1))
    }

    @Test
    @DisplayName("context resolve applies 6-day 8-slot defaults without explicit rests")
    fun contextResolve_sixDayDefault() {
        val context =
            GenerationContextTestFixtures.contextWith(
                profileOverrides = mapOf("trainingDaysPerWeek" to 6),
            )
        val timeline = context.resolveTrainingTimeline()
        assertEquals(8, timeline.cycleLength)
        assertEquals(setOf(3, 7), timeline.restSlotIndices)
        assertEquals(listOf(0, 1, 2, 4, 5, 6), timeline.trainingSlotIndices)

        val resolved = context.withResolvedCycleSpace()
        assertEquals(8, resolved.cycleLength)
        assertEquals(setOf(3, 7), resolved.restSlotIndices)
    }
}
