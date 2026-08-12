package com.stratum

import com.stratum.domain.lift.Weight
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DashboardHealthUiTest {

    @Test
    @DisplayName("step counts use grouping separators")
    fun formatSteps() {
        assertEquals("8,432", formatStepCount(8_432L))
        assertEquals("0", formatStepCount(0L))
    }

    @Test
    @DisplayName("sleep duration is hours and minutes")
    fun formatSleep() {
        assertEquals("7h 12m", formatHoursMinutes(Duration.ofHours(7).plusMinutes(12)))
        assertEquals("45m", formatHoursMinutes(Duration.ofMinutes(45)))
    }

    @Test
    @DisplayName("weight delta keeps a signed tabular amount")
    fun formatDelta() {
        assertEquals("+1.2 lb", formatWeightDelta(1.2, Weight.WeightUnit.Lb))
        assertEquals("−0.4 kg", formatWeightDelta(-0.4, Weight.WeightUnit.Kg))
    }

    @Test
    @DisplayName("short date uses a compact month-day form")
    fun formatDate() {
        val instant = Instant.parse("2026-08-12T12:00:00Z")
        assertEquals("Aug 12", formatShortDate(instant, ZoneOffset.UTC))
    }
}
