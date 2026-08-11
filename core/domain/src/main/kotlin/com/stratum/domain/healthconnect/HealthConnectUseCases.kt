package com.stratum.domain.healthconnect

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

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
