package com.stratum.domain.healthconnect

import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.flow.Flow

/** Port for reading and persisting Health Connect integration preferences. */
interface HealthConnectPreferencesPort {
    fun observePreferences(): Flow<HealthConnectPreferences>
    suspend fun getPreferences(): HealthConnectPreferences
    suspend fun setEnabled(enabled: Boolean): Result<Unit>
    suspend fun setPendingSyncError(message: String?): Result<Unit>
}

/** Port for interacting with Health Connect SDK (pushing exercise records & permission checks). */
interface HealthConnectSyncPort {
    suspend fun isAvailable(): Boolean
    suspend fun hasWritePermission(): Boolean
    /** Currently granted Health Connect permissions, or a failure if the SDK cannot be queried. */
    suspend fun grantedPermissions(): Result<Set<String>>
    suspend fun pushSession(session: TrainingSession, sets: List<LoggedSet>): Result<Unit>
}

/**
 * Port for scheduling background sync jobs (WorkManager boundary).
 *
 * Implementations must be prefs-gated and must not throw — finalize treats this as fire-and-forget.
 */
interface HealthConnectSchedulerPort {
    suspend fun enqueueSync(sessionId: TrainingSessionId)
}

/** Port for durable sync-error surfacing to UI (sticky until cleared). */
interface HealthConnectSyncStatusPort {
    fun observePendingSyncErrors(): Flow<String>
    suspend fun reportSyncError(message: String)
    suspend fun clearPendingSyncError()
}

/** Port for reading Health Connect metrics for the dashboard pager. */
interface HealthConnectMetricsPort {
    suspend fun isAvailable(): Boolean
    suspend fun grantedReadTypes(): Set<HealthMetricType>
    suspend fun readSnapshot(request: HealthMetricsRequest): HealthMetricsSnapshot
}
