package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WorkoutCompleteUiStateShareTest {

    @Nested
    @DisplayName("Loaded.toShareContent")
    inner class ToShareContent {

        @Test
        @DisplayName("Maps Loaded state to WorkoutShareContent preserving PR count and truncation")
        fun mapsLoadedToShareContent() {
            val bestSets =
                listOf(
                    LiftBestSetUi(
                        liftName = "Barbell Bench Press",
                        performanceLabel = "225 lb × 5",
                        rirLabel = "1",
                        estimatedOneRmLabel = "253",
                        isPersonalRecord = true,
                    ),
                    LiftBestSetUi(
                        liftName = "Incline Dumbbell Press",
                        performanceLabel = "80 lb × 8",
                        rirLabel = "2",
                        estimatedOneRmLabel = "101",
                        isPersonalRecord = false,
                    ),
                )
            val loaded =
                WorkoutCompleteUiState.Loaded(
                    workoutName = "Push Day",
                    durationFormatted = "1h 05m",
                    bestSets = bestSets,
                )

            val shareContent = loaded.toShareContent()

            assertEquals("Push Day", shareContent.workoutName)
            assertEquals("1h 05m", shareContent.durationFormatted)
            assertEquals(1, shareContent.personalRecordCount)
            assertEquals(2, shareContent.visibleSets.size)
            assertEquals("Barbell Bench Press", shareContent.visibleSets[0].liftName)
            assertEquals("225 lb × 5", shareContent.visibleSets[0].performanceLabel)
            assertTrue(shareContent.visibleSets[0].isPersonalRecord)
            assertEquals("Incline Dumbbell Press", shareContent.visibleSets[1].liftName)
            assertEquals("80 lb × 8", shareContent.visibleSets[1].performanceLabel)
            assertEquals(false, shareContent.visibleSets[1].isPersonalRecord)
            assertEquals(0, shareContent.overflowCount)
        }

        @Test
        @DisplayName("Respects custom maxLifts override when mapping")
        fun respectsMaxLifts() {
            val sets =
                List(8) { index ->
                    LiftBestSetUi(
                        liftName = "Lift $index",
                        performanceLabel = "100 × 5",
                        rirLabel = "2",
                        estimatedOneRmLabel = "115",
                        isPersonalRecord = index == 7,
                    )
                }
            val loaded =
                WorkoutCompleteUiState.Loaded(
                    workoutName = "Full Body",
                    durationFormatted = "1h 30m",
                    bestSets = sets,
                )

            val shareContent = loaded.toShareContent(maxLifts = 3)

            assertEquals(3, shareContent.visibleSets.size)
            assertEquals(5, shareContent.overflowCount)
            assertEquals(1, shareContent.personalRecordCount)
        }
    }
}
