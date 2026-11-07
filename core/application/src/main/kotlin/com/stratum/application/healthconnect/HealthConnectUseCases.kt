package com.stratum.application.healthconnect

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.application.diagnostics.OperationalDiagnostic

const val HEALTH_CONNECT_PREFS_TAG = "HealthConnectPrefs"
const val HEALTH_CONNECT_METRICS_TAG = "HealthConnectMetrics"

/**
 * Outcome of enabling/disabling Health Connect (STR-243; STR-237 Route (i)).
 *
 * On a failed write the port's [HealthSyncDiagnostic]s are re-rendered as
 * [OperationalDiagnostic]s on the [PersistenceFailed] branch (legacy tag +
 * message, real throwable as cause); the presentation boundary drains them
 * exactly once. Coroutine cancellation is not a branch: it propagates as
 * cancellation.
 */
sealed interface SetHealthConnectEnabledResult {
    data object Success : SetHealthConnectEnabledResult

    /** The Health Connect SDK is not available on this device; no write was attempted. */
    data object SdkUnavailable : SetHealthConnectEnabledResult

    /** The preferences write failed (store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : SetHealthConnectEnabledResult
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

/** Outcome of clearing the sticky Health Connect sync error (STR-243; STR-237 Route (i)). */
sealed interface ClearHealthConnectSyncErrorResult {
    data object Success : ClearHealthConnectSyncErrorResult

    /** The sync-status write failed (store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ClearHealthConnectSyncErrorResult
}

class ObserveHealthConnectPreferencesUseCase @Inject constructor(
    private val preferencesPort: HealthConnectPreferencesPort,
) {
    operator fun invoke(): Flow<HealthConnectPreferences> = preferencesPort.observePreferences()
}

class SetHealthConnectEnabledUseCase @Inject constructor(
    private val preferencesPort: HealthConnectPreferencesPort,
    private val syncPort: HealthConnectSyncPort,
) {
    suspend operator fun invoke(enabled: Boolean): SetHealthConnectEnabledResult {
        if (enabled && !syncPort.isAvailable()) {
            return SetHealthConnectEnabledResult.SdkUnavailable
        }
        // The preferences repository returns write failures as an outcome
        // without logging them. Each failure diagnostic is re-rendered onto
        // the outcome branch as an OperationalDiagnostic; the Settings
        // ViewModel (or applyGrantedPermissions' boundary) drains it exactly
        // once.
        return when (val outcome = preferencesPort.setEnabled(enabled)) {
            HealthConnectWriteOutcome.Success -> SetHealthConnectEnabledResult.Success
            is HealthConnectWriteOutcome.Failed ->
                SetHealthConnectEnabledResult.PersistenceFailed(
                    diagnostics = outcome.diagnostics.map { diagnostic ->
                        when (diagnostic) {
                            is HealthSyncDiagnostic.OperationFailed ->
                                OperationalDiagnostic(
                                    tag = HEALTH_CONNECT_PREFS_TAG,
                                    message = "Failed to set Health Connect enabled: " +
                                        "${diagnostic.cause.message}",
                                    cause = diagnostic.cause,
                                )
                        }
                    },
                )
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
                // The Failed invariant guarantees at least one diagnostic,
                // and OperationFailed is its only subtype.
                val cause = outcome.diagnostics
                    .filterIsInstance<HealthSyncDiagnostic.OperationFailed>()
                    .first().cause
                return ApplyGrantedPermissionsOutcome.Applied(
                    diagnostics = listOf(
                        ApplyGrantedPermissionsDiagnostic.PermissionQueryFailed(cause = cause),
                    ),
                )
            }
        }
        return when (val enabledResult = invoke(granted.any { it in requested })) {
            SetHealthConnectEnabledResult.Success -> ApplyGrantedPermissionsOutcome.Applied()
            SetHealthConnectEnabledResult.SdkUnavailable ->
                ApplyGrantedPermissionsOutcome.SdkUnavailable
            is SetHealthConnectEnabledResult.PersistenceFailed ->
                ApplyGrantedPermissionsOutcome.PersistenceFailed(enabledResult.diagnostics)
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
) {
    suspend operator fun invoke(): ClearHealthConnectSyncErrorResult {
        // The sync-status repository returns write failures as an outcome
        // without logging them. Each failure diagnostic is re-rendered onto
        // the outcome branch as an OperationalDiagnostic; the calling
        // boundary (Settings / Workout-complete ViewModel) drains it exactly
        // once — callers must not ignore a PersistenceFailed branch.
        return when (val outcome = syncStatusPort.clearPendingSyncError()) {
            HealthConnectWriteOutcome.Success -> ClearHealthConnectSyncErrorResult.Success
            is HealthConnectWriteOutcome.Failed ->
                ClearHealthConnectSyncErrorResult.PersistenceFailed(
                    diagnostics = outcome.diagnostics.map { diagnostic ->
                        when (diagnostic) {
                            is HealthSyncDiagnostic.OperationFailed ->
                                OperationalDiagnostic(
                                    tag = HEALTH_CONNECT_PREFS_TAG,
                                    message = "Failed to set Health Connect sync error: " +
                                        "${diagnostic.cause.message}",
                                    cause = diagnostic.cause,
                                )
                        }
                    },
                )
        }
    }
}
