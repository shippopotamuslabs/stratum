package com.stratum.core.data.mapper

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SpecializationPreferenceMapperTest {

    @Test
    @DisplayName("Given specialized muscles when mapped then they round-trip")
    fun specializedMusclesRoundTrip() {
        val preference = SpecializationPreference(
            levels = mapOf(
                MuscleGroup.Chest to SpecializationLevel.Specialize,
                MuscleGroup.Back to SpecializationLevel.Specialize,
            ),
        )

        val roundTripped = preference.toEntities().toDomain()

        assertEquals(preference.specializedMuscles, roundTripped.specializedMuscles)
    }

    @Test
    @DisplayName("Given Maintenance levels when mapped to entities then they are filtered out")
    fun maintenanceLevelsFilteredOnPersist() {
        val preference = SpecializationPreference(
            levels = mapOf(
                MuscleGroup.Chest to SpecializationLevel.Specialize,
                MuscleGroup.Back to SpecializationLevel.Maintenance,
            ),
        )

        val entities = preference.toEntities()

        assertEquals(1, entities.size)
        assertEquals(MuscleGroup.Chest, entities.single().muscle)
    }

    @Test
    @DisplayName("Given an empty preference when mapped then it round-trips to Empty")
    fun emptyRoundTrips() {
        assertEquals(SpecializationPreference.Empty, emptyList<com.stratum.core.data.db.SpecializationPreferenceEntity>().toDomain())
        assertTrue(SpecializationPreference.Empty.toEntities().isEmpty())
    }
}
