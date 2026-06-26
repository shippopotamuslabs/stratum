package com.stratum.domain.response

import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class IntensityProxyCalculatorTest {
    @Test
    @DisplayName("RP-S2-001 RIR-first intensity at 75 percent coverage")
    fun rpS2_001_rirFirstIntensityAtThreshold() {
        val input = ResponseFixtures.fixS2RirWeek()
        val cleanSets = TrainingSignalCleaner.clean(input)

        val intensity = IntensityProxyCalculator.forWeek(cleanSets, ResponseFixtures.liftBench)

        assertEquals(2.0, intensity)
    }

    @Test
    @DisplayName("RP-S2-002 e1RM proxy fallback below 70 percent RIR")
    fun rpS2_002_e1rmProxyFallback() {
        val input = ResponseFixtures.fixS2NoRirWeek()
        val cleanSets = TrainingSignalCleaner.clean(input)

        val intensity = IntensityProxyCalculator.forWeek(cleanSets, ResponseFixtures.liftBench)

        assertNotNull(intensity)
        assertEquals(1.0, intensity!!, 0.01)
        assertNotEquals(2.0, intensity)
    }
}
