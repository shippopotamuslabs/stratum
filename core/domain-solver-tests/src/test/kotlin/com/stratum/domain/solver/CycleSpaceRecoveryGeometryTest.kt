@file:Suppress("MagicNumber", "FunctionNaming")

package com.stratum.domain.solver

import com.stratum.core.testing.program.GenerationContextTestFixtures
import com.stratum.domain.program.generation.context.resolveTrainingTimeline
import com.stratum.domain.program.generation.context.withResolvedCycleSpace
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import com.stratum.domain.program.schedule.GenerationCycleDefaults
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Locks cycle-space recovery geometry: solver day assignment follows resolved train slots
 * (not synthetic Mon–Sat), so mid-block and end-of-cycle rests create gap ≥ 2.
 */
class CycleSpaceRecoveryGeometryTest {
    @Test
    @DisplayName("6-in-8 default: cycle slots are 0,1,2,4,5,6 with mid and wrap gap 2")
    fun sixDayEightSlot_assignmentUsesTrainSlots() =
        runBlocking {
            val context =
                GenerationContextTestFixtures
                    .contextWith(
                        profileOverrides =
                            mapOf(
                                "trainingDaysPerWeek" to 6,
                                "weekdays" to
                                    listOf(
                                        "Monday",
                                        "Tuesday",
                                        "Wednesday",
                                        "Thursday",
                                        "Friday",
                                        "Saturday",
                                    ),
                            ),
                    ).withResolvedCycleSpace()
            assertEquals(8, context.cycleLength)
            assertEquals(GenerationCycleDefaults.SIX_DAY_ROTATING_DEFAULT_RESTS, context.restSlotIndices)

            val timeline = context.resolveTrainingTimeline()
            assertEquals(listOf(0, 1, 2, 4, 5, 6), timeline.trainingSlotIndices)
            assertEquals(2, timeline.gap(2, 4))
            assertEquals(2, timeline.gap(6, 0))
            assertFalse(timeline.adjacentTrainingPairs().any { (a, b) -> a == 2 && b == 4 })
            assertFalse(timeline.adjacentTrainingPairs().any { (a, b) -> a == 6 && b == 0 })

            val synthesizer = CpSatProgramSynthesizer(deadlineMillis = 30_000L)
            val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)
            assertEquals(
                listOf(0, 1, 2, 4, 5, 6),
                synthesis.cycleSlotIndexByPosition.sorted(),
            )
            assertEquals(listOf(0, 1, 2, 4, 5, 6), synthesis.cycleSlotIndexByPosition)
        }

    @Test
    @DisplayName("6-in-7: train 0..5 with wrap gap 2")
    fun sixDaySevenSlot_assignment() =
        runBlocking {
            val context =
                GenerationContextTestFixtures
                    .contextWith(
                        profileOverrides =
                            mapOf(
                                "trainingDaysPerWeek" to 6,
                                "weekdays" to
                                    listOf(
                                        "Monday",
                                        "Tuesday",
                                        "Wednesday",
                                        "Thursday",
                                        "Friday",
                                        "Saturday",
                                    ),
                            ),
                    ).copy(
                        cycleLength = 7,
                        restSlotIndices = setOf(6),
                    ).withResolvedCycleSpace()

            val timeline = context.resolveTrainingTimeline()
            assertEquals(7, timeline.cycleLength)
            assertEquals(listOf(0, 1, 2, 3, 4, 5), timeline.trainingSlotIndices)
            assertEquals(2, timeline.gap(5, 0))

            val synthesizer = CpSatProgramSynthesizer(deadlineMillis = 30_000L)
            val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)
            assertEquals(listOf(0, 1, 2, 3, 4, 5), synthesis.cycleSlotIndexByPosition)
        }

    @Test
    @DisplayName("gap-1 wrap layout includes last→first in adjacent pairs")
    fun gap1Wrap_adjacentPairs() {
        val timeline =
            com.stratum.domain.program.schedule.TrainingTimeline(
                cycleLength = 7,
                trainingSlotIndices = listOf(0, 1, 2, 3, 4, 6),
            )
        assertEquals(1, timeline.gap(6, 0))
        assertTrue(timeline.adjacentTrainingPairs().contains(6 to 0))
    }
}
