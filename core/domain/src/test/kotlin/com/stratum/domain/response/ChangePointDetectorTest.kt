package com.stratum.domain.response

import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

class ChangePointDetectorTest {
    @Test
    @DisplayName("RP-S3-005 binseg requires 6 week segments")
    fun rpS3_005_binsegRequiresSixWeekSegments() {
        val weekStarts =
            (0 until 10).map { ResponseFixtures.weekStart(2025, 1, 6).plus(it * 7L, ChronoUnit.DAYS) }
        val volumes = listOf(10.0, 10.0, 10.0, 10.0, 4.0, 4.0, 4.0, 4.0, 4.0, 4.0)
        val signals =
            weekStarts.zip(volumes).map { (start, volume) ->
                ResponseFixtures.weeklySignal(
                    weekStart = start,
                    muscle = com.stratum.domain.onboarding.MuscleGroup.Quad,
                    hardSets = volume,
                    peakEstimatedOneRm = volume,
                    smoothedEstimatedOneRm = volume,
                )
            }
        val boundaries = ChangePointDetector.binarySegment(signals)

        assertEquals(0, boundaries.size)
        val week4Start = weekStarts[3]
        val week5Start = weekStarts[4]
        assertTrue(boundaries.none { it.isAfter(week4Start) && it.isBefore(week5Start) })
    }
}
