package com.stratum.domain.program.generation.split

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FullBodyLowerAdjacencyTest {

    @Test
    fun penalizesFullBodyOnMondayAndLowerOnTuesday() {
        val exposures =
            listOf(
                MuscleSessionExposure(
                    muscle = "Quad",
                    weekdayIndex = 0,
                    fractionalSets = 8.0,
                    workoutName = "Full Body A",
                    sessionType = SessionTypeId.FullBody,
                ),
                MuscleSessionExposure(
                    muscle = "Quad",
                    weekdayIndex = 1,
                    fractionalSets = 10.0,
                    workoutName = "Lower A",
                    sessionType = SessionTypeId.Lower,
                ),
            )
        assertEquals(1, fullBodyLowerAdjacentCount(exposures))
        assertTrue(recoveryScheduleSummary(exposures).fullBodyLowerAdjacentCount == 1)
    }

    @Test
    fun spacedFullBodyAndLowerDoNotIncurAdjacencyPenalty() {
        val exposures =
            listOf(
                MuscleSessionExposure(
                    muscle = "Quad",
                    weekdayIndex = 0,
                    fractionalSets = 8.0,
                    workoutName = "Full Body A",
                    sessionType = SessionTypeId.FullBody,
                ),
                MuscleSessionExposure(
                    muscle = "Quad",
                    weekdayIndex = 2,
                    fractionalSets = 10.0,
                    workoutName = "Lower A",
                    sessionType = SessionTypeId.Lower,
                ),
            )
        assertEquals(0, fullBodyLowerAdjacentCount(exposures))
    }
}
