package com.stratum.core.ui.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WorkoutShareContentTest {

    @Nested
    @DisplayName("buildWorkoutShareContent")
    inner class Build {

        @Test
        @DisplayName("Given empty best sets when built then overflow is zero and PR count is zero")
        fun emptyBestSets() {
            val content =
                buildWorkoutShareContent(
                    workoutName = "Push",
                    durationFormatted = "45m",
                    liftRows = emptyList(),
                )

            assertEquals("Push", content.workoutName)
            assertEquals("45m", content.durationFormatted)
            assertEquals(0, content.personalRecordCount)
            assertTrue(content.visibleSets.isEmpty())
            assertEquals(0, content.overflowCount)
        }

        @Test
        @DisplayName("Given fewer lifts than cap when built then all visible and no overflow")
        fun underCap() {
            val sets = List(3) { index -> shareRow("Lift $index", isPr = index == 0) }

            val content =
                buildWorkoutShareContent(
                    workoutName = "Pull",
                    durationFormatted = "1h",
                    liftRows = sets,
                    maxLifts = 7,
                )

            assertEquals(3, content.visibleSets.size)
            assertEquals(0, content.overflowCount)
            assertEquals(1, content.personalRecordCount)
            assertEquals(
                listOf(
                    ShareLiftRowUi("Lift 0", "100 × 5", true),
                    ShareLiftRowUi("Lift 1", "100 × 5", false),
                    ShareLiftRowUi("Lift 2", "100 × 5", false),
                ),
                content.visibleSets,
            )
        }

        @Test
        @DisplayName("Given more lifts than cap when built then truncates and reports overflow")
        fun overCap() {
            val sets = List(10) { index -> shareRow("Lift $index", isPr = index >= 8) }

            val content =
                buildWorkoutShareContent(
                    workoutName = "Legs",
                    durationFormatted = "1h 20m",
                    liftRows = sets,
                    maxLifts = 7,
                )

            assertEquals(7, content.visibleSets.size)
            assertEquals(3, content.overflowCount)
            // PRs are on indices 8 and 9 — still counted from the full list
            assertEquals(2, content.personalRecordCount)
            assertEquals("Lift 0", content.visibleSets.first().liftName)
            assertEquals("Lift 6", content.visibleSets.last().liftName)
        }

        @Test
        @DisplayName("Given maxLifts zero when built then only overflow count remains")
        fun zeroCap() {
            val sets = List(4) { shareRow("Lift $it") }

            val content =
                buildWorkoutShareContent(
                    workoutName = "Full",
                    durationFormatted = "30m",
                    liftRows = sets,
                    maxLifts = 0,
                )

            assertTrue(content.visibleSets.isEmpty())
            assertEquals(4, content.overflowCount)
        }

        @Test
        @DisplayName("Given negative maxLifts when built then throws")
        fun negativeCapThrows() {
            assertThrows<IllegalArgumentException> {
                buildWorkoutShareContent(
                    workoutName = "X",
                    durationFormatted = "1m",
                    liftRows = emptyList(),
                    maxLifts = -1,
                )
            }
        }

        @Test
        @DisplayName("Given explicit overridePersonalRecordCount when built then uses override")
        fun explicitPrOverride() {
            val sets = List(3) { shareRow("Lift $it", isPr = false) }

            val content =
                buildWorkoutShareContent(
                    workoutName = "Session",
                    durationFormatted = "1h",
                    liftRows = sets,
                    overridePersonalRecordCount = 5,
                )

            assertEquals(5, content.personalRecordCount)
        }
    }

    private fun shareRow(
        name: String,
        isPr: Boolean = false,
    ): ShareLiftRowUi =
        ShareLiftRowUi(
            liftName = name,
            performanceLabel = "100 × 5",
            isPersonalRecord = isPr,
        )
}
