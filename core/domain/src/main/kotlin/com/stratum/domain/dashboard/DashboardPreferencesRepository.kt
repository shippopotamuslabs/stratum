package com.stratum.domain.dashboard

import kotlinx.coroutines.flow.Flow

/**
 * Observes and updates persisted [DashboardPreferences]. Implementations typically use
 * Jetpack DataStore; callers must not block the main thread on writes.
 */
interface DashboardPreferencesRepository {
    fun observe(): Flow<DashboardPreferences>

    suspend fun setSelectedTab(tab: DashboardBoardTab)
}
