package com.stratum.application.healthconnect

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthConnectUnavailableException

class ObserveHealthConnectPreferencesUseCase @Inject constructor(
    private val preferencesPort: HealthConnectPreferencesPort,
) {
    operator fun invoke(): Flow<HealthConnectPreferences> = preferencesPort.observePreferences()
}

class SetHealthConnectEnabledUseCase @Inject constructor(
    private val preferencesPort: HealthConnectPreferencesPort,
    private val syncPort: HealthConnectSyncPort,
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> {
        if (enabled && !syncPort.isAvailable()) {
            return Result.failure(HealthConnectUnavailableException())
        }
        return preferencesPort.setEnabled(enabled)
    }

    /**
     * After the permission sheet closes, opt in from the current SDK grant set. A query failure
     * leaves the preference unchanged so Back / cancel cannot turn an enabled switch off.
     */
    suspend fun applyGrantedPermissions(requested: Set<String>): Result<Unit> {
        val granted = syncPort.grantedPermissions().getOrElse { return Result.success(Unit) }
        return invoke(granted.any { it in requested })
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
) {
    suspend operator fun invoke() {
        syncStatusPort.clearPendingSyncError()
    }
}
