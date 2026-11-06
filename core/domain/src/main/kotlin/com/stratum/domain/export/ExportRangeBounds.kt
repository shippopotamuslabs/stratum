package com.stratum.domain.export

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Outcome of resolving an [ExportLogRange] to instant bounds (STR-243).
 *
 * Failure branches carry typed values only — never a [Throwable].
 */
sealed interface ExportRangeBoundsResult {
    /** Resolved bounds as `[startInstant, endInstantExclusive)`; null pair means AllTime (no bounds). */
    data class Resolved(val bounds: Pair<Instant, Instant>?) : ExportRangeBoundsResult

    /** A custom range whose start date falls after its end date. */
    data class StartAfterEnd(
        val startDate: LocalDate,
        val endDateInclusive: LocalDate,
    ) : ExportRangeBoundsResult
}

/**
 * Pure helpers: range → `[startInstant, endInstantExclusive)?`.
 * Null pair means AllTime (no bounds).
 */
object ExportRangeBounds {
    fun resolve(
        range: ExportLogRange,
        now: Instant = Instant.now(),
    ): ExportRangeBoundsResult {
        return when (range) {
            ExportLogRange.AllTime -> ExportRangeBoundsResult.Resolved(null)
            is ExportLogRange.LastDays -> {
                val today = todayInZone(range.zone, now)
                val startDate = today.minusDays((range.days - 1).toLong())
                ExportRangeBoundsResult.Resolved(boundsForCalendar(startDate, today, range.zone))
            }
            is ExportLogRange.YearToDate -> {
                val today = todayInZone(range.zone, now)
                val startDate = LocalDate.of(today.year, 1, 1)
                ExportRangeBoundsResult.Resolved(boundsForCalendar(startDate, today, range.zone))
            }
            is ExportLogRange.Custom -> {
                if (range.startDate > range.endDateInclusive) {
                    ExportRangeBoundsResult.StartAfterEnd(range.startDate, range.endDateInclusive)
                } else {
                    ExportRangeBoundsResult.Resolved(
                        boundsForCalendar(range.startDate, range.endDateInclusive, range.zone),
                    )
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
