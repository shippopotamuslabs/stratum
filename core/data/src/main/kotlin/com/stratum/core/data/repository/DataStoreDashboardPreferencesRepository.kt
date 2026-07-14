package com.stratum.core.data.repository

import android.content.Context
import com.stratum.core.data.dashboard.dashboardPreferencesDataStore
import com.stratum.core.data.dashboard.toDomain
import com.stratum.core.data.dashboard.toStorageName
import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.dashboard.DashboardPreferences
import com.stratum.domain.dashboard.DashboardPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [DashboardPreferencesRepository]. Emits defaults (Muscles board) until the
 * first successful read; unknown stored tab names map back to Muscles.
 */
@Singleton
class DataStoreDashboardPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DashboardPreferencesRepository {

    override fun observe(): Flow<DashboardPreferences> =
        context.dashboardPreferencesDataStore.data.map { record -> record.toDomain() }

    override suspend fun setSelectedTab(tab: DashboardBoardTab) {
        context.dashboardPreferencesDataStore.updateData { current ->
            current.copy(selectedTab = tab.toStorageName())
        }
    }
}
