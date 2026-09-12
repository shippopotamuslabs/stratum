package com.stratum.domain.export

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure helpers: range → `[startInstant, endInstantExclusive)?`.
 * Null pair means AllTime (no bounds).
 */
object ExportRangeBounds {
    fun resolve(
        range: ExportLogRange,
        now: Instant = Instant.now(),
    ): Result<Pair<Instant, Instant>?> {
        return when (range) {
            ExportLogRange.AllTime -> Result.success(null)
            is ExportLogRange.LastDays -> {
                val today = todayInZone(range.zone, now)
                val startDate = today.minusDays((range.days - 1).toLong())
                Result.success(boundsForCalendar(startDate, today, range.zone))
            }
            is ExportLogRange.YearToDate -> {
                val today = todayInZone(range.zone, now)
                val startDate = LocalDate.of(today.year, 1, 1)
                Result.success(boundsForCalendar(startDate, today, range.zone))
            }
            is ExportLogRange.Custom -> {
                if (range.startDate > range.endDateInclusive) {
                    Result.failure(IllegalArgumentException("Start date must be on or before end date."))
                } else {
                    Result.success(boundsForCalendar(range.startDate, range.endDateInclusive, range.zone))
                }
            }
        }
    }

    fun todayInZone(zone: ZoneId, now: Instant): LocalDate =
        now.atZone(zone).toLocalDate()

    private fun boundsForCalendar(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zone: ZoneId,
    ): Pair<Instant, Instant> {
        val startInstant = startDate.atStartOfDay(zone).toInstant()
        val endInstantExclusive = endDateInclusive.plusDays(1).atStartOfDay(zone).toInstant()
        return startInstant to endInstantExclusive
    }
}
