package com.stratum.domain.importing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

/** Parses timestamps from LLM-produced import JSON (ISO-8601 instant or date-only). */
object ImportTimestamps {
    private val SPACE_DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendLiteral(' ')
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .toFormatter()

    fun parseOrNull(raw: String): Instant? {
        val trimmed = raw.trim()
        try {
            return Instant.parse(trimmed)
        } catch (_: Exception) {
        }
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        } catch (_: Exception) {
        }
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        } catch (_: Exception) {
        }
        try {
            return LocalDateTime.parse(trimmed, SPACE_DATE_TIME_FORMATTER)
                .toInstant(ZoneOffset.UTC)
        } catch (_: Exception) {
        }
        return null
    }

    fun parse(raw: String): Instant =
        parseOrNull(raw) ?: throw IllegalArgumentException("Unsupported timestamp format: $raw")
}

