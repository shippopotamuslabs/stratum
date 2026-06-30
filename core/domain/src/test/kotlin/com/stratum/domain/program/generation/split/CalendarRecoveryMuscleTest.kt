package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.reference.minimumRecoveryCalendarGap
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CalendarRecoveryMuscleTest {

    @Test
    @DisplayName("calendar recovery penalizes triceps on consecutive lower and push days")
    fun penalizesTricepBackToBack() {
        val weekdays = listOf(0, 1, 2) // Mon, Tue, Wed
        val tight = listOf(
            MuscleSessionExposure("Tricep", 0, 2.0),
            MuscleSessionExposure("Quad", 0, 12.0),
            MuscleSessionExposure("Tricep", 1, 10.0),
            MuscleSessionExposure("Chest", 1, 10.0),
            MuscleSessionExposure("Back", 2, 10.0),
        )
        val spread = listOf(
            MuscleSessionExposure("Tricep", 0, 2.0),
            MuscleSessionExposure("Quad", 0, 12.0),
            MuscleSessionExposure("Back", 1, 10.0),
            MuscleSessionExposure("Tricep", 2, 10.0),
            MuscleSessionExposure("Chest", 2, 10.0),
        )
        val tightScore = calendarRecoveryScoreFromExposures(tight)
        val spreadScore = calendarRecoveryScoreFromExposures(spread)
        assertTrue(
            spreadScore > tightScore,
            "spread=$spreadScore should beat tight=$tightScore; " +
                "required gap=${minimumRecoveryCalendarGap("Tricep", 2.0, 10.0)}",
        )
    }
}
