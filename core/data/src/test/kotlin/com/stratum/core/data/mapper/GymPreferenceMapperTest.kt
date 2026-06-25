package com.stratum.core.data.mapper

import com.stratum.domain.lift.GymPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GymPreferenceMapperTest {

    @Test
    @DisplayName("Given a selected preset when mapped then it round-trips")
    fun selectedPresetRoundTrips() {
        val entity = GymPreset.CommercialGym.toEntity()

        assertEquals(GymPreset.CommercialGym, entity.toDomain())
    }

    @Test
    @DisplayName("Given a null preset when mapped then the entity stores null")
    fun nullPresetRoundTrips() {
        val entity = (null as GymPreset?).toEntity()

        assertNull(entity.selectedPreset)
        assertNull(entity.toDomain())
    }

    @Test
    @DisplayName("Given a missing entity when mapped then domain is null")
    fun missingEntityMapsToNull() {
        assertNull((null as com.stratum.core.data.db.GymPreferenceEntity?).toDomain())
    }
}
