package com.stratum.core.data.repository

import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import com.stratum.application.healthconnect.HealthConnectSyncStatusPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

/**
 * Durable sync-error bus backed by Health Connect preferences DataStore.
 * Sticky until [clearPendingSyncError] so late UI collectors still see the failure.
 */
@Singleton
class HealthConnectSyncStatusRepository @Inject constructor(
    private val preferencesPort: HealthConnectPreferencesPort,
) : HealthConnectSyncStatusPort {

    override fun observePendingSyncErrors(): Flow<String> =
        preferencesPort.observePreferences()
            .mapNotNull { prefs -> prefs.pendingSyncError }
            .distinctUntilChanged()

    override suspend fun reportSyncError(message: String) {
        preferencesPort.setPendingSyncError(message)
    }

    override suspend fun clearPendingSyncError() {
        preferencesPort.setPendingSyncError(null)
    }
}
