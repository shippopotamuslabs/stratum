package com.stratum.core.testing.healthconnect

import com.stratum.domain.healthconnect.HealthConnectSyncStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

class FakeHealthConnectSyncStatusPort : HealthConnectSyncStatusPort {
    private val pendingError = MutableStateFlow<String?>(null)

    val currentPendingError: String?
        get() = pendingError.value

    override fun observePendingSyncErrors(): Flow<String> =
        pendingError.mapNotNull { it }.distinctUntilChanged()

    override suspend fun reportSyncError(message: String) {
        pendingError.value = message
    }

    override suspend fun clearPendingSyncError() {
        pendingError.value = null
    }
}
