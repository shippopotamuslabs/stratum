package com.stratum.application.healthconnect

import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.domain.health.HealthMetricType
import com.stratum.domain.health.HealthMetricsRequest
import com.stratum.domain.health.HealthMetricsSnapshot

/**
 * Port for reading and persisting Health Connect integration preferences.
 *
 * Writes are plain suspend calls (STR-243): an operational failure throws to the
 * caller, whose use-case boundary translates it into its sealed outcome. The
 * repository never logs — the use case is the drain boundary.
 */
interface HealthConnectPreferencesPort {
    fun observePreferences(): Flow<HealthConnectPreferences>
    suspend fun getPreferences(): HealthConnectPreferences
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setPendingSyncError(kind: HealthFailureKind?)
}

/**
 * Port for interacting with Health Connect SDK (pushing exercise records & permission checks).
 *
 * Operational failures throw the application-owned [HealthSyncFailure] classification
 * (STR-243): the adapter translates vendor SDK throwables at this boundary, so no
 * vendor type and no raw-`Result` failure payload crosses the port.
 */
interface HealthConnectSyncPort {
    suspend fun isAvailable(): Boolean
    suspend fun hasWritePermission(): Boolean
    /** Currently granted Health Connect permissions; throws [HealthSyncFailure] if the SDK cannot be queried. */
    suspend fun grantedPermissions(): Set<String>
    suspend fun pushSession(session: TrainingSession, sets: List<LoggedSet>)
}

/**
 * Port for scheduling background sync jobs (WorkManager boundary).
 *
 * Implementations must be prefs-gated and must not throw — finalize treats this as fire-and-forget.
 */
interface HealthConnectSchedulerPort {
    suspend fun enqueueSync(sessionId: TrainingSessionId)
}

/** Port for durable sync-error surfacing to UI (sticky until cleared). Writes throw on failure. */
interface HealthConnectSyncStatusPort {
    fun observePendingSyncErrors(): Flow<HealthFailureKind>
    suspend fun reportSyncError(kind: HealthFailureKind)
    suspend fun clearPendingSyncError()
}

/** Port for reading Health Connect metrics for the dashboard pager. */
interface HealthConnectMetricsPort {
    suspend fun isAvailable(): Boolean
    suspend fun grantedReadTypes(): Set<HealthMetricType>
    suspend fun readSnapshot(request: HealthMetricsRequest): HealthMetricsSnapshot
}
