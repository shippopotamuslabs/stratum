package com.stratum.domain.program.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GenerationCycleDefaultsTest {
    @Test
    @DisplayName("defaultCycleLength is 7 for classic N, 8 for six-day")
    fun defaultCycleLength() {
        assertEquals(7, GenerationCycleDefaults.defaultCycleLength(4))
        assertEquals(8, GenerationCycleDefaults.defaultCycleLength(6))
    }

    @Test
    @DisplayName("classic 3-day default rests leave Day 1/3/5 train slots")
    fun classicThreeDayRests() {
        assertEquals(
            setOf(1, 3, 5, 6),
            GenerationCycleDefaults.defaultRestSlotIndices(cycleLength = 7, trainingDayCount = 3),
        )
    }

    @Test
    @DisplayName("6-day 8-slot defaults mid + end rests")
    fun sixDayEightRests() {
        assertEquals(
            setOf(3, 7),
            GenerationCycleDefaults.defaultRestSlotIndices(cycleLength = 8, trainingDayCount = 6),
        )
    }

    @Test
    @DisplayName("6-day 7-slot defaults end rest")
    fun sixDaySevenRests() {
        assertEquals(
            setOf(6),
            GenerationCycleDefaults.defaultRestSlotIndices(cycleLength = 7, trainingDayCount = 6),
        )
    }

    @Test
    @DisplayName("resolveSixDayHints respects explicit 7-day toggle")
    fun sixDaySevenToggle() {
        val hints =
            GenerationCycleDefaults.resolveSixDayHints(
                trainingDayCount = 6,
                cycleLength = 7,
            )
        assertEquals(7, hints?.cycleLength)
        assertEquals(setOf(6), hints?.restSlotIndices)
    }

    @Test
    @DisplayName("resolveGenerationHints keeps explicit rest placement")
    fun explicitRestsPreserved() {
        val hints =
            GenerationCycleDefaults.resolveGenerationHints(
                trainingDayCount = 6,
                cycleLength = 8,
                restSlotIndices = setOf(2, 5),
            )
        assertEquals(8, hints?.cycleLength)
        assertEquals(setOf(2, 5), hints?.restSlotIndices)
    }

    @Test
    @DisplayName("inferTrainingDayCount throws for ambiguous classic 7")
    fun inferClassicThrows() {
        assertThrows(IllegalStateException::class.java) {
            GenerationCycleDefaults.inferTrainingDayCount(7)
        }
    }

    @Test
    @DisplayName("inferTrainingDayCount maps 8 → 6")
    fun inferEight() {
        assertEquals(6, GenerationCycleDefaults.inferTrainingDayCount(8))
    }
}
