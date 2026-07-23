package com.stratum.domain.response

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calendar-day bucketing for daily volume series (dashboard 7-Day + detail charts).
 * Single source of truth for day starts and zero-fill so board and detail cannot drift.
 *
 * Days are the supplied zone's calendar days. Each bucket key is that date's local midnight as an
 * [Instant] (idempotent under the same [ZoneId]). Callers at the Android edge supply
 * [ZoneId.systemDefault]; pure helpers require an explicit [zone] so tests never depend on host TZ.
 */
object DayBucketSupport {
    /**
     * Start of the calendar day containing [instant] in [zone] (local midnight → Instant).
     */
    fun dayStartFor(instant: Instant, zone: ZoneId): Instant {
        val date = instant.atZone(zone).toLocalDate()
        return date.atStartOfDay(zone).toInstant()
    }

    /**
     * [dayCount] consecutive local calendar days ending on [asOf]'s day in [zone] (oldest first).
     * Values for missing days are `0.0`. Multiple points on the same [LocalDate] are **summed**.
     * Iterates [LocalDate]s so DST transitions stay one civil day.
     */
    fun zeroFillDaily(
        points: List<Pair<Instant, Double>>,
        asOf: Instant,
        dayCount: Int,
        zone: ZoneId,
    ): List<Pair<Instant, Double>> {
        if (dayCount <= 0) return points.sortedBy { it.first }
        val endDate = asOf.atZone(zone).toLocalDate()
        val startDate = endDate.minusDays((dayCount - 1).toLong())
        val byDay = LinkedHashMap<LocalDate, Double>()
        for ((day, value) in points) {
            val date = day.atZone(zone).toLocalDate()
            byDay[date] = (byDay[date] ?: 0.0) + value
        }
        return (0 until dayCount).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            date.atStartOfDay(zone).toInstant() to (byDay[date] ?: 0.0)
        }
    }
}
