package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.slots.SessionTypeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpperPullLowerAdjacencyTest {

    @Test
    fun countsUpperPullOnMondayAndLowerOnTuesday() {
        val exposures =
            listOf(
                MuscleSessionExposure(
                    muscle = "Back",
                    weekdayIndex = 0,
                    fractionalSets = 10.0,
                    workoutName = "Upper Pull A",
                    sessionType = SessionTypeId.UpperPull,
                ),
                MuscleSessionExposure(
                    muscle = "Quad",
                    weekdayIndex = 1,
                    fractionalSets = 12.0,
                    workoutName = "Lower A",
                    sessionType = SessionTypeId.Lower,
                ),
            )
        assertEquals(1, upperPullAdjacentLowerCount(exposures))
    }

    @Test
    fun spacedUpperPullAndLowerDoNotIncurAdjacencyPenalty() {
        val exposures =
            listOf(
                MuscleSessionExposure(
                    muscle = "Back",
                    weekdayIndex = 0,
                    fractionalSets = 10.0,
                    workoutName = "Upper Pull A",
                    sessionType = SessionTypeId.UpperPull,
                ),
                MuscleSessionExposure(
                    muscle = "Quad",
                    weekdayIndex = 2,
                    fractionalSets = 12.0,
                    workoutName = "Lower A",
                    sessionType = SessionTypeId.Lower,
                ),
            )
        assertEquals(0, upperPullAdjacentLowerCount(exposures))
    }
}
