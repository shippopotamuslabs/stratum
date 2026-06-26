package com.stratum.domain.importing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Parses timestamps from LLM-produced import JSON (ISO-8601 instant or date-only). */
object ImportTimestamps {
    fun parse(raw: String): Instant {
        val trimmed = raw.trim()
        runCatching { return Instant.parse(trimmed) }
        runCatching {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        }
        runCatching {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        }
        throw IllegalArgumentException("Unsupported timestamp format: $raw")
    }
}
