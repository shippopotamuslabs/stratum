package com.stratum.domain.export

import java.time.LocalDate
import java.time.ZoneId

/** Device-local calendar bounds for log export. */
sealed interface ExportLogRange {
    /** Inclusive device-local calendar bounds using [zone]. */
    data class Custom(
        val startDate: LocalDate,
        val endDateInclusive: LocalDate,
        val zone: ZoneId,
    ) : ExportLogRange

    data class LastDays(val days: Int, val zone: ZoneId) : ExportLogRange

    data class YearToDate(val zone: ZoneId) : ExportLogRange

    data object AllTime : ExportLogRange
}
