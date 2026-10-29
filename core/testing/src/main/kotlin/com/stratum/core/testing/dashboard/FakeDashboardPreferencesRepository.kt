package com.stratum.core.testing.dashboard

import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.dashboard.DashboardPreferences
import com.stratum.application.dashboard.DashboardPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [DashboardPreferencesRepository] for ViewModel tests. */
class FakeDashboardPreferencesRepository(
    initial: DashboardPreferences = DashboardPreferences(),
) : DashboardPreferencesRepository {

    private val preferences = MutableStateFlow(initial)

    val current: DashboardPreferences
        get() = preferences.value

    fun seed(value: DashboardPreferences) {
        preferences.value = value
    }

    fun seed(tab: DashboardBoardTab) {
        preferences.value = preferences.value.copy(selectedTab = tab)
    }

    override fun observe(): Flow<DashboardPreferences> = preferences.asStateFlow()

    override suspend fun setSelectedTab(tab: DashboardBoardTab) {
        preferences.value = preferences.value.copy(selectedTab = tab)
    }
}
