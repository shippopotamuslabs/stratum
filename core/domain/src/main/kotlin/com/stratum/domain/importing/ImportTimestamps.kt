package com.stratum.domain.importing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException

/** Parses timestamps from LLM-produced import JSON (ISO-8601 instant or date-only). */
object ImportTimestamps {
    private val SPACE_DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendLiteral(' ')
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .toFormatter()

    fun parseOrNull(raw: String): Instant? {
        val trimmed = raw.trim()
        return attempt { Instant.parse(trimmed) }
            ?: attempt {
                LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
            }
            ?: attempt {
                LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC)
            }
            ?: attempt {
                LocalDateTime.parse(trimmed, SPACE_DATE_TIME_FORMATTER)
                    .toInstant(ZoneOffset.UTC)
            }
    }

    /** A format miss is a normal answer: only a parse failure returns null here. */
    private fun attempt(parse: () -> Instant): Instant? =
        try {
            parse()
        } catch (_: DateTimeParseException) {
            null
        }
}
