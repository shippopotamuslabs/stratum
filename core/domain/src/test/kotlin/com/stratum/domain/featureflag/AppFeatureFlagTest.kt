package com.stratum.domain.featureflag

import com.stratum.core.testing.featureflag.FakeFeatureFlagRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AppFeatureFlagTest {

    private val fakeFlags = FakeFeatureFlagRepository()

    @BeforeEach
    fun setUp() {
        fakeFlags.clear()
    }

    @Test
    @DisplayName("Given the equipment flag when read then its key matches the Remote Config parameter")
    fun equipmentFlagKeyMatchesRemoteConfigParameter() {
        assertEquals("equipment_management_v2", AppFeatureFlag.EQUIPMENT_MANAGEMENT_V2.key)
    }

    @Test
    @DisplayName("Given a fresh repository when the equipment flag is checked then it is disabled")
    fun equipmentFlagIsDisabledByDefault() {
        assertFalse(fakeFlags.isEnabled(AppFeatureFlag.EQUIPMENT_MANAGEMENT_V2))
    }

    @Test
    @DisplayName("Given the equipment flag enabled when checked then it is enabled")
    fun equipmentFlagIsEnabledWhenSet() {
        fakeFlags.setFlag(AppFeatureFlag.EQUIPMENT_MANAGEMENT_V2, true)

        assertTrue(fakeFlags.isEnabled(AppFeatureFlag.EQUIPMENT_MANAGEMENT_V2))
    }
}
