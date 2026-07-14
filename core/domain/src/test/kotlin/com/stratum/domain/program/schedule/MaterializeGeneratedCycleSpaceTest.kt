package com.stratum.domain.program.schedule

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MaterializeGeneratedCycleSpaceTest {
    @Nested
    @DisplayName("explicit rest hints (unified gen)")
    inner class ExplicitHints {
        @Test
        @DisplayName("N=3 + 7-slot Day 1/3/5 rests")
        fun threeDaySevenSlot() {
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = 3,
                    restSlotIndicesHint = setOf(1, 3, 5, 6),
                    cycleLengthHint = 7,
                )
            assertEquals(7, space.cycleLength)
            assertEquals(setOf(1, 3, 5, 6), space.restSlotIndices)
            assertEquals(3, space.trainingSlotCount)
        }

        @Test
        @DisplayName("N=4 + 7-slot with three rests")
        fun fourDaySevenSlot() {
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = 4,
                    restSlotIndicesHint = setOf(2, 5, 6),
                    cycleLengthHint = 7,
                )
            assertEquals(7, space.cycleLength)
            assertEquals(setOf(2, 5, 6), space.restSlotIndices)
        }
    }

    @Nested
    @DisplayName("defaults from train count only (no weekdays)")
    inner class Defaults {
        @Test
        @DisplayName("N=3 → 7-slot classic default rests")
        fun threeDayDefaults() {
            val space = materializeGeneratedCycleSpace(trainingSlotCount = 3)
            assertEquals(7, space.cycleLength)
            assertEquals(setOf(1, 3, 5, 6), space.restSlotIndices)
        }

        @Test
        @DisplayName("N=4 → 7-slot classic default rests")
        fun fourDayDefaults() {
            val space = materializeGeneratedCycleSpace(trainingSlotCount = 4)
            assertEquals(7, space.cycleLength)
            assertEquals(setOf(2, 5, 6), space.restSlotIndices)
        }

        @Test
        @DisplayName("N=5 → 7-slot classic default rests")
        fun fiveDayDefaults() {
            val space = materializeGeneratedCycleSpace(trainingSlotCount = 5)
            assertEquals(7, space.cycleLength)
            assertEquals(setOf(2, 6), space.restSlotIndices)
        }

        @Test
        @DisplayName("N=6 without rests → 8-slot rotating defaults")
        fun sixDayDefaults() {
            val space = materializeGeneratedCycleSpace(trainingSlotCount = 6)
            assertEquals(8, space.cycleLength)
            assertEquals(setOf(3, 7), space.restSlotIndices)
        }
    }

    @Nested
    @DisplayName("6-day path accepts rest pattern for 7- or 8-slot spaces")
    inner class SixDay {
        @Test
        @DisplayName("explicit 8-slot PPL rest pattern at 3 and 7")
        fun sixDayEightSlotPattern() {
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = 6,
                    restSlotIndicesHint = setOf(3, 7),
                    cycleLengthHint = 8,
                )
            assertEquals(8, space.cycleLength)
            assertEquals(setOf(3, 7), space.restSlotIndices)
        }

        @Test
        @DisplayName("explicit 7-slot pattern with one rest")
        fun sixDaySevenSlotPattern() {
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = 6,
                    restSlotIndicesHint = setOf(6),
                    cycleLengthHint = 7,
                )
            assertEquals(7, space.cycleLength)
            assertEquals(setOf(6), space.restSlotIndices)
        }

        @Test
        @DisplayName("rests alone infer cycleLength = train + rests")
        fun restsAloneInferCycleLength() {
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = 6,
                    restSlotIndicesHint = setOf(3, 7),
                )
            assertEquals(8, space.cycleLength)
            assertEquals(setOf(3, 7), space.restSlotIndices)
        }

        @Test
        @DisplayName("cycleLengthHint 7 without rests uses fixed-week default rest")
        fun sixDaySevenHintWithoutRests() {
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = 6,
                    cycleLengthHint = 7,
                )
            assertEquals(7, space.cycleLength)
            assertEquals(setOf(6), space.restSlotIndices)
        }

        @Test
        @DisplayName("mismatched rest hint falls through to defaults keeping train count N")
        fun mismatchedHintFallsThroughToDefaults() {
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = 5,
                    restSlotIndicesHint = setOf(3, 7),
                    cycleLengthHint = 8,
                )
            assertEquals(8, space.cycleLength)
            assertEquals(setOf(5, 6, 7), space.restSlotIndices)
            assertEquals(5, space.trainingSlotCount)
        }
    }

    @Nested
    @DisplayName("Program.withMaterializedCycleSpace")
    inner class ProgramExtension {
        @Test
        @DisplayName("re-derives cycle from workout count + existing rests")
        fun reDerivesFromTrainCount() {
            val program =
                Program(
                    id = ProgramId("p-1"),
                    name = "Classic",
                    createdAt = Instant.parse("2026-06-16T12:00:00Z"),
                    cycleLength = 7,
                    restSlotIndices = setOf(1, 3, 5, 6),
                    workouts =
                        listOf(
                            workout("w-0", 0, Weekday.Monday),
                            workout("w-1", 1, Weekday.Wednesday),
                            workout("w-2", 2, Weekday.Friday),
                        ),
                )
            val materialised = program.withMaterializedCycleSpace()
            assertEquals(7, materialised.cycleLength)
            assertEquals(setOf(1, 3, 5, 6), materialised.restSlotIndices)
        }
    }

    private fun workout(id: String, position: Int, weekday: Weekday): Workout =
        Workout(
            id = WorkoutId(id),
            programId = ProgramId("p-1"),
            name = "W$position",
            weekday = weekday,
            position = position,
            workoutLifts = emptyList(),
        )
}
