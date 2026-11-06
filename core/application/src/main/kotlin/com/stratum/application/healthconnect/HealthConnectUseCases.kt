package com.stratum.application.healthconnect

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.domain.logging.LoggingPort

const val HEALTH_CONNECT_PREFS_TAG = "HealthConnectPrefs"
const val HEALTH_CONNECT_METRICS_TAG = "HealthConnectMetrics"

/**
 * Outcome of enabling/disabling Health Connect (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface SetHealthConnectEnabledResult {
    data object Success : SetHealthConnectEnabledResult

    /** The Health Connect SDK is not available on this device; no write was attempted. */
    data object SdkUnavailable : SetHealthConnectEnabledResult

    /** The preferences write failed (store I/O). */
    data object PersistenceFailed : SetHealthConnectEnabledResult
}

/**
 * Outcome of a Health Connect granted-permissions query (STR-243). A query
 * failure is an expected branch ([QueryFailed]) so the UI can fall back to
 * requesting the full permission set; it is not logged here — callers that
 * tolerate the degradation record it as a diagnostic (see
 * [SetHealthConnectEnabledUseCase.applyGrantedPermissions]).
 */
sealed interface HealthConnectPermissionsQueryResult {
    data class Success(val permissions: Set<String>) : HealthConnectPermissionsQueryResult

    data object QueryFailed : HealthConnectPermissionsQueryResult
}

/** Outcome of clearing the sticky Health Connect sync error (STR-243). */
sealed interface ClearHealthConnectSyncErrorResult {
    data object Success : ClearHealthConnectSyncErrorResult

    /** The sync-status write failed (store I/O). */
    data object PersistenceFailed : ClearHealthConnectSyncErrorResult
}

class ObserveHealthConnectPreferencesUseCase @Inject constructor(
    private val preferencesPort: HealthConnectPreferencesPort,
) {
    operator fun invoke(): Flow<HealthConnectPreferences> = preferencesPort.observePreferences()
}

class SetHealthConnectEnabledUseCase @Inject constructor(
    private val preferencesPort: HealthConnectPreferencesPort,
    private val syncPort: HealthConnectSyncPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(enabled: Boolean): SetHealthConnectEnabledResult {
        if (enabled && !syncPort.isAvailable()) {
            return SetHealthConnectEnabledResult.SdkUnavailable
        }
        // The preferences repository returns write failures as an outcome
        // without logging them. This use case is the drain boundary — its
        // only callers are the Settings ViewModel (which branches on the
        // outcome for UI state) and applyGrantedPermissions below — so each
        // failure diagnostic is logged exactly once here.
        return when (val outcome = preferencesPort.setEnabled(enabled)) {
            HealthConnectWriteOutcome.Success -> SetHealthConnectEnabledResult.Success
            is HealthConnectWriteOutcome.Failed -> {
                outcome.diagnostics.forEach { diagnostic ->
                    when (diagnostic) {
                        is HealthSyncDiagnostic.OperationFailed ->
                            loggingPort.error(
                                HEALTH_CONNECT_PREFS_TAG,
                                "Failed to set Health Connect enabled: ${diagnostic.cause.message}",
                                diagnostic.cause,
                            )
                    }
                }
                SetHealthConnectEnabledResult.PersistenceFailed
            }
        }
    }

    /**
     * After the permission sheet closes, opt in from the current SDK grant set. A query failure
     * leaves the preference unchanged so Back / cancel cannot turn an enabled switch off — the
     * failure rides along as a diagnostic on the applied outcome (STR-257).
     */
    suspend fun applyGrantedPermissions(requested: Set<String>): ApplyGrantedPermissionsOutcome {
        val granted = when (val outcome = syncPort.grantedPermissions()) {
            is HealthConnectGrantedPermissionsOutcome.Success -> outcome.permissions
            is HealthConnectGrantedPermissionsOutcome.Failed -> {
                // A query failure leaves the preference unchanged so Back /
                // cancel cannot turn an enabled switch off — the failure rides
                // along as a diagnostic on the applied outcome (STR-257). The
                // diagnostic cause is the port failure's own diagnostic cause.
                val cause = outcome.diagnostics
                    .filterIsInstance<HealthSyncDiagnostic.OperationFailed>()
                    .firstOrNull()?.cause
                    ?: IllegalStateException(outcome.message)
                return ApplyGrantedPermissionsOutcome.Applied(
                    diagnostics = listOf(
                        ApplyGrantedPermissionsDiagnostic.PermissionQueryFailed(cause = cause),
                    ),
                )
            }
        }
        return when (invoke(granted.any { it in requested })) {
            SetHealthConnectEnabledResult.Success -> ApplyGrantedPermissionsOutcome.Applied()
            SetHealthConnectEnabledResult.SdkUnavailable ->
                ApplyGrantedPermissionsOutcome.SdkUnavailable
            SetHealthConnectEnabledResult.PersistenceFailed ->
                ApplyGrantedPermissionsOutcome.PersistenceFailed
        }
    }

    /**
     * Permissions in [requested] that Health Connect has not granted yet. A query failure is
     * returned as [HealthConnectPermissionsQueryResult.QueryFailed] so the UI can fall back
     * to requesting the full set.
     */
    suspend fun permissionsStillNeeded(requested: Set<String>): HealthConnectPermissionsQueryResult {
        val granted = when (val outcome = syncPort.grantedPermissions()) {
            is HealthConnectGrantedPermissionsOutcome.Success -> outcome.permissions
            is HealthConnectGrantedPermissionsOutcome.Failed ->
                return HealthConnectPermissionsQueryResult.QueryFailed
        }
        return HealthConnectPermissionsQueryResult.Success(requested - granted)
    }

    suspend fun grantedPermissions(): HealthConnectPermissionsQueryResult =
        when (val outcome = syncPort.grantedPermissions()) {
            is HealthConnectGrantedPermissionsOutcome.Success ->
                HealthConnectPermissionsQueryResult.Success(outcome.permissions)
            is HealthConnectGrantedPermissionsOutcome.Failed ->
                HealthConnectPermissionsQueryResult.QueryFailed
        }
}

class ObserveHealthConnectSyncErrorsUseCase @Inject constructor(
    private val syncStatusPort: HealthConnectSyncStatusPort,
) {
    operator fun invoke(): Flow<HealthFailureKind> = syncStatusPort.observePendingSyncErrors()
}

class ClearHealthConnectSyncErrorUseCase @Inject constructor(
    private val syncStatusPort: HealthConnectSyncStatusPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): ClearHealthConnectSyncErrorResult {
        // The sync-status repository returns write failures as an outcome
        // without logging them. This use case is the drain boundary — its
        // only callers are the Settings and Workout-complete ViewModels (which
        // do not log) — so each failure diagnostic is logged exactly once
        // here. Callers may ignore the returned outcome since the failure is
        // already logged here.
        return when (val outcome = syncStatusPort.clearPendingSyncError()) {
            HealthConnectWriteOutcome.Success -> ClearHealthConnectSyncErrorResult.Success
            is HealthConnectWriteOutcome.Failed -> {
                outcome.diagnostics.forEach { diagnostic ->
                    when (diagnostic) {
                        is HealthSyncDiagnostic.OperationFailed ->
                            loggingPort.error(
                                HEALTH_CONNECT_PREFS_TAG,
                                "Failed to set Health Connect sync error: ${diagnostic.cause.message}",
                                diagnostic.cause,
                            )
                    }
                }
                ClearHealthConnectSyncErrorResult.PersistenceFailed
            }
        }
    }
}
