package com.stratum.core.infrastructure.db

import androidx.room.TypeConverter

/**
 * Stores a `Set<Int>` as a comma-separated CSV of decimal integers. Empty set ↔ empty string.
 * Used for [ProgramEntity.restSlotIndices] (schema v1 reinstall — AGENTS.md §4.5).
 */
class IntSetConverter {
    @TypeConverter
    fun fromIntSet(values: Set<Int>): String =
        values.sorted().joinToString(SEPARATOR) { it.toString() }

    @TypeConverter
    fun toIntSet(value: String?): Set<Int> {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(SEPARATOR)
            .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() }
            .toSet()
    }

    private companion object {
        const val SEPARATOR = ","
    }
}
