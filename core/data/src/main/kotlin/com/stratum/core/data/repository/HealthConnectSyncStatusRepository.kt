package com.stratum.core.data.repository

import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import com.stratum.application.healthconnect.HealthConnectSyncStatusPort
import com.stratum.domain.healthconnect.HealthFailureKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Durable sync-error bus backed by Health Connect preferences DataStore.
 * Sticky until [clearPendingSyncError] so late UI collectors still see the failure.
 */
@Singleton
class HealthConnectSyncStatusRepository @Inject constructor(
    private val preferencesPort: HealthConnectPreferencesPort,
) : HealthConnectSyncStatusPort {

    override fun observePendingSyncErrors(): Flow<HealthFailureKind> =
        preferencesPort.observePreferences()
            .map { prefs -> prefs.pendingSyncError }
            .distinctUntilChanged()
            .filterNotNull()

    override suspend fun reportSyncError(kind: HealthFailureKind): Result<Unit> =
        preferencesPort.setPendingSyncError(kind)

    override suspend fun clearPendingSyncError(): Result<Unit> =
        preferencesPort.setPendingSyncError(null)
}
