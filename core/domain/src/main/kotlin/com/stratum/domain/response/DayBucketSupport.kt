package com.stratum.domain.response

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * UTC calendar-day bucketing for daily volume series (dashboard 7-Day + detail charts).
 * Single source of truth for day starts and zero-fill so board and detail cannot drift.
 */
object DayBucketSupport {
    fun dayStartFor(instant: Instant): Instant {
        val date = instant.atZone(ZoneOffset.UTC).toLocalDate()
        return date.atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    /**
     * [dayCount] consecutive UTC days ending on [asOf]'s day (oldest first). Values for missing
     * days are `0.0`.
     */
    fun zeroFillDaily(
        points: List<Pair<Instant, Double>>,
        asOf: Instant,
        dayCount: Int,
    ): List<Pair<Instant, Double>> {
        if (dayCount <= 0) return points.sortedBy { it.first }
        val endDay = dayStartFor(asOf)
        val startDay = endDay.minus((dayCount - 1).toLong(), ChronoUnit.DAYS)
        val byDay = points.associate { (day, value) -> dayStartFor(day) to value }
        return (0 until dayCount).map { offset ->
            val day = startDay.plus(offset.toLong(), ChronoUnit.DAYS)
            day to (byDay[day] ?: 0.0)
        }
    }
}
