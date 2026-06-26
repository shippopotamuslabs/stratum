package com.stratum.domain.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class VolumeLandmarkSummaryBuilderTest {
    @Test
    @DisplayName("centered rolling mean damps a single outlier week")
    fun centeredRollingMeanDampsSingleOutlier() {
        // Baseline 200 for 5 weeks with a one-week spike to 400 in the middle.
        val values = listOf(200.0, 200.0, 400.0, 200.0, 200.0)
        val smoothed = VolumeLandmarkSummaryBuilder.centeredRollingMean(values, halfWindow = 1)

        assertEquals(5, smoothed.size)
        // The spike week's smoothed value is the mean of [200, 400, 200] = 266.67, not 400.
        assertEquals(266.67, smoothed[2]!!, 0.01)
        // Endpoints use a 2-value window: mean of [200, 200] = 200.
        assertEquals(200.0, smoothed[0]!!, 0.001)
        assertEquals(200.0, smoothed[4]!!, 0.001)
    }

    @Test
    @DisplayName("centered rolling mean skips nulls without breaking the window")
    fun centeredRollingMeanSkipsNulls() {
        val values = listOf<Double?>(200.0, null, 220.0, null, 240.0)
        val smoothed = VolumeLandmarkSummaryBuilder.centeredRollingMean(values, halfWindow = 1)

        // Middle index window [null, 220, null] -> 220 only.
        assertEquals(220.0, smoothed[2]!!, 0.001)
        // First window [200, null] -> 200 only.
        assertEquals(200.0, smoothed[0]!!, 0.001)
    }

    @Test
    @DisplayName("centered rolling mean returns null only when the whole window is null")
    fun centeredRollingMeanAllNullWindow() {
        val values = listOf<Double?>(null, null, null)
        val smoothed = VolumeLandmarkSummaryBuilder.centeredRollingMean(values, halfWindow = 1)
        assertTrue(smoothed.all { it == null })
        assertNull(smoothed[1])
    }

    @Test
    @DisplayName("centered rolling mean handles a single value")
    fun centeredRollingMeanSingleValue() {
        val smoothed = VolumeLandmarkSummaryBuilder.centeredRollingMean(listOf(150.0), halfWindow = 1)
        assertEquals(listOf(150.0), smoothed)
    }
}