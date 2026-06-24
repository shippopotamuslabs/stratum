package com.stratum.core.data.db

import androidx.room.TypeConverter
import com.stratum.domain.onboarding.Weekday

/**
 * Stores a `Set<Weekday>` as a comma-separated CSV of enum names. An empty set is stored
 * as the empty string (round-trips to `emptySet()`).
 *
 * Registered on the Room database via `@TypeConverters` on [StratumDatabase].
 */
class WeekdaySetConverter {
    @TypeConverter
    fun fromWeekdaySet(weekdays: Set<Weekday>): String =
        weekdays.joinToString(SEPARATOR) { it.name }

    @TypeConverter
    fun toWeekdaySet(value: String?): Set<Weekday> {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(SEPARATOR)
            .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.let { runCatching { Weekday.valueOf(it) }.getOrNull() } }
            .toSet()
    }

    private companion object {
        const val SEPARATOR = ","
    }
}
