package com.stratum.feature.workout.ui

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
                    bestSets = emptyList(),
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
            val sets = List(3) { index -> bestSet("Lift $index", isPr = index == 0) }

            val content =
                buildWorkoutShareContent(
                    workoutName = "Pull",
                    durationFormatted = "1h",
                    bestSets = sets,
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
            val sets = List(10) { index -> bestSet("Lift $index", isPr = index >= 8) }

            val content =
                buildWorkoutShareContent(
                    workoutName = "Legs",
                    durationFormatted = "1h 20m",
                    bestSets = sets,
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
        @DisplayName("Given custom maxLifts via toShareContent when built then respects override")
        fun customMaxLiftsOnLoaded() {
            val loaded =
                WorkoutCompleteUiState.Loaded(
                    workoutName = "Custom",
                    durationFormatted = "10m",
                    bestSets = List(5) { bestSet("Lift $it") },
                )

            val content = loaded.toShareContent(maxLifts = 2)

            assertEquals(2, content.visibleSets.size)
            assertEquals(3, content.overflowCount)
        }

        @Test
        @DisplayName("Given maxLifts zero when built then only overflow count remains")
        fun zeroCap() {
            val sets = List(4) { bestSet("Lift $it") }

            val content =
                buildWorkoutShareContent(
                    workoutName = "Full",
                    durationFormatted = "30m",
                    bestSets = sets,
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
                    bestSets = emptyList(),
                    maxLifts = -1,
                )
            }
        }

        @Test
        @DisplayName("Maps only share fields onto ShareLiftRowUi")
        fun mapsShareOnlyFields() {
            val content =
                buildWorkoutShareContent(
                    workoutName = "P",
                    durationFormatted = "1m",
                    bestSets =
                        listOf(
                            LiftBestSetUi(
                                liftName = "Bench",
                                performanceLabel = "185 × 3",
                                rirLabel = "1",
                                estimatedOneRmLabel = "201",
                                isPersonalRecord = true,
                            ),
                        ),
                )

            assertEquals(
                ShareLiftRowUi(
                    liftName = "Bench",
                    performanceLabel = "185 × 3",
                    isPersonalRecord = true,
                ),
                content.visibleSets.single(),
            )
        }
    }

    @Nested
    @DisplayName("Loaded.toShareContent")
    inner class FromLoaded {

        @Test
        @DisplayName("Maps Loaded summary fields into share content")
        fun mapsLoaded() {
            val loaded =
                WorkoutCompleteUiState.Loaded(
                    workoutName = "Upper A",
                    durationFormatted = "55m 12s",
                    bestSets =
                        listOf(
                            bestSet("Bench", isPr = true),
                            bestSet("Row"),
                        ),
                    equipmentProposals = emptyList(),
                    repRangeProposals = emptyList(),
                )

            val content = loaded.toShareContent()

            assertEquals("Upper A", content.workoutName)
            assertEquals("55m 12s", content.durationFormatted)
            assertEquals(1, content.personalRecordCount)
            assertEquals(2, content.visibleSets.size)
            assertEquals(0, content.overflowCount)
        }
    }

    private fun bestSet(
        name: String,
        isPr: Boolean = false,
    ): LiftBestSetUi =
        LiftBestSetUi(
            liftName = name,
            performanceLabel = "100 × 5",
            rirLabel = "2",
            estimatedOneRmLabel = "115",
            isPersonalRecord = isPr,
        )
}
