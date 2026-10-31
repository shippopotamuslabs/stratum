package com.stratum.core.infrastructure.program

import com.stratum.domain.program.ProgramsUiPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ProgramsUiPreferencesMapper")
class ProgramsUiPreferencesMapperTest {

    @Test
    @DisplayName("record to domain preserves expanded workout id")
    fun recordToDomain() {
        val domain = ProgramsUiPreferencesRecord(expandedWorkoutId = "w-1").toDomain()
        assertEquals(ProgramsUiPreferences(expandedWorkoutId = "w-1"), domain)
    }

    @Test
    @DisplayName("domain to record preserves null expanded workout id")
    fun domainToRecordNull() {
        val record = ProgramsUiPreferences().toRecord()
        assertEquals(ProgramsUiPreferencesRecord(), record)
    }

    @Test
    @DisplayName("round-trip keeps expanded workout id")
    fun roundTrip() {
        val original = ProgramsUiPreferences(expandedWorkoutId = "w-legs")
        assertEquals(original, original.toRecord().toDomain())
    }
}
