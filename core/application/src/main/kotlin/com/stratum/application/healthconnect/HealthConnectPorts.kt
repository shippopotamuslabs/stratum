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
 * A Health Connect operation failure recorded as data (§10 diagnostics
 * channel). A boundary that owns the failure's log record drains each
 * diagnostic through `LoggingPort.error` with the throwable attached;
 * diagnostics never reach UI state.
 */
sealed interface HealthSyncDiagnostic {
    data class OperationFailed(
        val cause: Throwable,
    ) : HealthSyncDiagnostic
}

/**
 * Outcome of a Health Connect store write (preferences / sync-status) at a
 * port (STR-243 remediation, Sean's E1 ruling): no exception crosses the port
 * in place of an outcome (coroutine cancellation still propagates). The
 * [Failed] branch carries the originating throwable only as a
 * [HealthSyncDiagnostic] value, for the boundary that logs it exactly once.
 */
sealed interface HealthConnectWriteOutcome {
    data object Success : HealthConnectWriteOutcome

    data class Failed(
        val diagnostics: List<HealthSyncDiagnostic>,
    ) : HealthConnectWriteOutcome
}

/** Outcome of a Health Connect granted-permissions query at the sync port. */
sealed interface HealthConnectGrantedPermissionsOutcome {
    data class Success(val permissions: Set<String>) : HealthConnectGrantedPermissionsOutcome

    data class Failed(
        val kind: HealthSyncFailureKind,
        val message: String?,
        val diagnostics: List<HealthSyncDiagnostic>,
    ) : HealthConnectGrantedPermissionsOutcome
}

/** Outcome of pushing a session to Health Connect at the sync port. */
sealed interface HealthConnectPushOutcome {
    data object Success : HealthConnectPushOutcome

    data class Failed(
        val kind: HealthSyncFailureKind,
        val message: String?,
        val diagnostics: List<HealthSyncDiagnostic>,
    ) : HealthConnectPushOutcome
}

/**
 * Port for reading and persisting Health Connect integration preferences.
 *
 * Writes return [HealthConnectWriteOutcome] (STR-243 remediation): the
 * repository never logs — the use case is the drain boundary for the
 * diagnostic the failure branch carries.
 */
interface HealthConnectPreferencesPort {
    fun observePreferences(): Flow<HealthConnectPreferences>
    suspend fun getPreferences(): HealthConnectPreferences
    suspend fun setEnabled(enabled: Boolean): HealthConnectWriteOutcome
    suspend fun setPendingSyncError(kind: HealthFailureKind?): HealthConnectWriteOutcome
}

/**
 * Port for interacting with Health Connect SDK (pushing exercise records & permission checks).
 *
 * Operations return their sealed outcomes (STR-243 remediation): the adapter
 * translates vendor SDK failures into the [HealthSyncFailureKind]
 * classification at this boundary, so no vendor type, no thrown
 * classification, and no raw-`Result` failure payload crosses the port.
 */
interface HealthConnectSyncPort {
    suspend fun isAvailable(): Boolean
    suspend fun hasWritePermission(): Boolean
    /** Currently granted Health Connect permissions; a failed SDK query is the outcome's Failed branch. */
    suspend fun grantedPermissions(): HealthConnectGrantedPermissionsOutcome
    suspend fun pushSession(
        session: TrainingSession,
        sets: List<LoggedSet>,
    ): HealthConnectPushOutcome
}

/**
 * Port for scheduling background sync jobs (WorkManager boundary).
 *
 * Implementations must be prefs-gated and must not throw — finalize treats this as fire-and-forget.
 */
interface HealthConnectSchedulerPort {
    suspend fun enqueueSync(sessionId: TrainingSessionId)
}

/** Port for durable sync-error surfacing to UI (sticky until cleared). Writes return [HealthConnectWriteOutcome]. */
interface HealthConnectSyncStatusPort {
    fun observePendingSyncErrors(): Flow<HealthFailureKind>
    suspend fun reportSyncError(kind: HealthFailureKind): HealthConnectWriteOutcome
    suspend fun clearPendingSyncError(): HealthConnectWriteOutcome
}

/** Port for reading Health Connect metrics for the dashboard pager. */
interface HealthConnectMetricsPort {
    suspend fun isAvailable(): Boolean
    suspend fun grantedReadTypes(): Set<HealthMetricType>
    suspend fun readSnapshot(request: HealthMetricsRequest): HealthMetricsSnapshot
}
