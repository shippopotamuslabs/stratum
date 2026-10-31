package com.stratum.domain.health

import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SleepZonesTest {

    private val zones = SleepZones(
        light = Duration.ofHours(3).plusMinutes(58),
        deep = Duration.ofHours(1).plusMinutes(31),
        rem = Duration.ofHours(2).plusMinutes(17),
        awake = Duration.ofMinutes(14),
    )

    @Test
    @DisplayName("STR-264: asleepWithin takes awake time out of the span")
    fun asleepWithinExcludesAwake() {
        // Sean's 2026-10-30/31 night: 8h in bed, 14m awake, 7h 46m asleep.
        assertEquals(
            Duration.ofHours(7).plusMinutes(46),
            zones.asleepWithin(Duration.ofHours(8)),
        )
    }

    @Test
    @DisplayName("STR-264: asleepWithin never goes negative")
    fun asleepWithinClampsAtZero() {
        assertEquals(Duration.ZERO, zones.asleepWithin(Duration.ofMinutes(10)))
    }

    @Test
    @DisplayName("STR-264: nightly duration is the span minus awake time")
    fun nightlyDurationExcludesAwake() {
        val nightly = NightlySleepDuration(
            dayStart = Instant.parse("2026-10-31T00:00:00Z"),
            span = Duration.ofHours(8),
            zones = zones,
        )
        assertEquals(Duration.ofHours(7).plusMinutes(46), nightly.duration)
    }

    @Test
    @DisplayName("STR-264: nightly duration is the span when there are no zones")
    fun nightlyDurationWithoutZonesIsSpan() {
        val nightly = NightlySleepDuration(
            dayStart = Instant.parse("2026-10-31T00:00:00Z"),
            span = Duration.ofHours(8),
            zones = null,
        )
        assertEquals(Duration.ofHours(8), nightly.duration)
    }

    @Test
    @DisplayName("STR-264: sleep night duration is start-to-end minus awake time")
    fun sleepNightDurationExcludesAwake() {
        val night = SleepNight(
            start = Instant.parse("2026-10-30T21:56:00Z"),
            end = Instant.parse("2026-10-31T05:56:00Z"),
            zones = zones,
        )
        assertEquals(Duration.ofHours(8), night.span)
        assertEquals(Duration.ofHours(7).plusMinutes(46), night.duration)
    }
}
