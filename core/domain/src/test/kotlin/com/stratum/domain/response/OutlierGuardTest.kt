package com.stratum.domain.response

import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

class OutlierGuardTest {
    @Test
    @DisplayName("RP-S2-004 rolling median smooths middle week")
    fun rpS2_004_rollingMedianSmoothsMiddleWeek() {
        val weekStarts =
            (0 until 3).map { ResponseFixtures.weekStart(2025, 1, 6).plus(it * 7L, ChronoUnit.DAYS) }
        val peaks = listOf(100.0, 200.0, 102.0)
        val signals =
            weekStarts.zip(peaks).map { (start, peak) ->
                ResponseFixtures.weeklySignal(
                    weekStart = start,
                    liftId = ResponseFixtures.liftBench,
                    peakEstimatedOneRm = peak,
                    smoothedEstimatedOneRm = peak,
                )
            }
        val winsorized = OutlierGuard.winsorizeWeeklySignals(signals)
        val week2 = winsorized.first { it.weekStart == weekStarts[1] }

        assertEquals(102.0, week2.smoothedEstimatedOneRm)
        assertNotEquals(200.0, week2.smoothedEstimatedOneRm)
    }

    @Test
    @DisplayName("RP-S2-005 outlier week winsorized")
    fun rpS2_005_outlierWeekWinsorized() {
        val before = ResponseFixtures.fixS2OutlierWeekly()
        val week3Start = before[2].weekStart
        val after = OutlierGuard.winsorizeWeeklySignals(before)
        val week3 = after.first { it.weekStart == week3Start }

        assertTrue(week3.qualityFlags.contains(TrainingSignalQualityFlag.OutlierWinsorized))
        assertEquals(200.0, before[2].peakEstimatedOneRm)
        assertTrue(week3.smoothedEstimatedOneRm!! < 200.0)
    }

    @Test
    @DisplayName("RP-S2-006 suspicious jump without gap")
    fun rpS2_006_suspiciousJumpWithoutGap() {
        val weekStarts =
            listOf(
                ResponseFixtures.weekStart(2025, 1, 6),
                ResponseFixtures.weekStart(2025, 1, 13),
            )
        val signals =
            listOf(
                ResponseFixtures.weeklySignal(
                    weekStart = weekStarts[0],
                    peakEstimatedOneRm = 100.0,
                ),
                ResponseFixtures.weeklySignal(
                    weekStart = weekStarts[1],
                    peakEstimatedOneRm = 120.0,
                ),
            )
        val flags = OutlierGuard.suspiciousJumpFlags(signals)

        assertEquals(1, flags.size)
        assertTrue(flags.contains(weekStarts[1]))
    }
}
