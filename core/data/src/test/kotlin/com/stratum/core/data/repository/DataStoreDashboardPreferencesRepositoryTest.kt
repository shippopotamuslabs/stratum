package com.stratum.core.data.repository

import app.cash.turbine.test
import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.dashboard.DashboardPreferences
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStoreDashboardPreferencesRepository]. The typed DataStore file is
 * process-scoped (one per Application), so each test resets to defaults in [setup].
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStoreDashboardPreferencesRepository")
class DataStoreDashboardPreferencesRepositoryTest {

    private lateinit var store: DataStoreDashboardPreferencesRepository

    @BeforeEach
    fun setup() = runTest {
        store = DataStoreDashboardPreferencesRepository(RuntimeEnvironment.getApplication())
        // Shared DataStore file across tests in this process — restore defaults first.
        store.setSelectedTab(DashboardBoardTab.Muscles)
    }

    @Test
    @DisplayName("observe emits Muscles by default")
    fun defaultTab() = runTest {
        store.observe().test {
            assertEquals(DashboardPreferences(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setSelectedTab persists Lifts and re-emits")
    fun setTabPersists() = runTest {
        store.observe().test {
            assertEquals(DashboardPreferences(), awaitItem())

            store.setSelectedTab(DashboardBoardTab.Lifts)

            assertEquals(
                DashboardPreferences(selectedTab = DashboardBoardTab.Lifts),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setSelectedTab persists SevenDay and re-emits")
    fun setSevenDayPersists() = runTest {
        store.setSelectedTab(DashboardBoardTab.SevenDay)

        store.observe().test {
            assertEquals(
                DashboardPreferences(selectedTab = DashboardBoardTab.SevenDay),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
