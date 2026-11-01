package com.stratum.core.infrastructure.dashboard

import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.dashboard.DashboardPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DashboardPreferencesMapper")
class DashboardPreferencesMapperTest {

    @Test
    @DisplayName("unknown storage name maps to Muscles")
    fun unknownStorageNameDefaultsToMuscles() {
        val domain = DashboardPreferencesRecord(selectedTab = "NotARealTab").toDomain()
        assertEquals(DashboardPreferences(selectedTab = DashboardBoardTab.Muscles), domain)
    }

    @Test
    @DisplayName("blank storage name maps to Muscles")
    fun blankStorageNameDefaultsToMuscles() {
        assertEquals(DashboardBoardTab.Muscles, "".toDashboardBoardTab())
    }

    @Test
    @DisplayName("known storage names round-trip through domain")
    fun knownNamesRoundTrip() {
        for (tab in DashboardBoardTab.entries) {
            val record = DashboardPreferences(selectedTab = tab).toRecord()
            assertEquals(tab, record.toDomain().selectedTab)
        }
    }
}
