package com.stratum.application.healthconnect

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.onFailureLogged

const val HEALTH_CONNECT_PREFS_TAG = "HealthConnectPrefs"

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
    suspend operator fun invoke(enabled: Boolean): Result<Unit> {
        if (enabled && !syncPort.isAvailable()) {
            return Result.failure(HealthSyncFailure.unavailable())
        }
        // The preferences repository returns write failures as values without
        // logging them. This use case is the drain boundary — its only callers
        // are the Settings ViewModel (which maps the Result to UI state) and
        // applyGrantedPermissions below — so each failure is logged exactly
        // once here.
        return preferencesPort.setEnabled(enabled).onFailureLogged(
            loggingPort,
            HEALTH_CONNECT_PREFS_TAG,
            "Failed to set Health Connect enabled",
        )
    }

    /**
     * After the permission sheet closes, opt in from the current SDK grant set. A query failure
     * leaves the preference unchanged so Back / cancel cannot turn an enabled switch off — the
     * failure rides along as a diagnostic on the success outcome (STR-257).
     */
    suspend fun applyGrantedPermissions(requested: Set<String>): Result<ApplyGrantedPermissionsOutcome> {
        val granted = syncPort.grantedPermissions().getOrElse { error ->
            // Cancellation is not a degraded query — it must cancel the caller.
            if (error is CancellationException) throw error
            return Result.success(
                ApplyGrantedPermissionsOutcome(
                    diagnostics = listOf(
                        ApplyGrantedPermissionsDiagnostic.PermissionQueryFailed(cause = error),
                    ),
                ),
            )
        }
        return invoke(granted.any { it in requested }).map { ApplyGrantedPermissionsOutcome() }
    }

    /**
     * Permissions in [requested] that Health Connect has not granted yet. A query failure is
     * returned so the UI can fall back to requesting the full set.
     */
    suspend fun permissionsStillNeeded(requested: Set<String>): Result<Set<String>> {
        val granted = syncPort.grantedPermissions().getOrElse { return Result.failure(it) }
        return Result.success(requested - granted)
    }

    suspend fun grantedPermissions(): Result<Set<String>> = syncPort.grantedPermissions()
}

class ObserveHealthConnectSyncErrorsUseCase @Inject constructor(
    private val syncStatusPort: HealthConnectSyncStatusPort,
) {
    operator fun invoke(): Flow<String> = syncStatusPort.observePendingSyncErrors()
}

class ClearHealthConnectSyncErrorUseCase @Inject constructor(
    private val syncStatusPort: HealthConnectSyncStatusPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): Result<Unit> {
        // The sync-status repository returns write failures as values without
        // logging them. This use case is the drain boundary — its only callers
        // are the Settings and Workout-complete ViewModels (which do not log)
        // — so each failure is logged exactly once here. Callers may ignore
        // the returned Result since the failure is already logged here.
        return syncStatusPort.clearPendingSyncError().onFailureLogged(
            loggingPort,
            HEALTH_CONNECT_PREFS_TAG,
            "Failed to set Health Connect sync error",
        )
    }
}
