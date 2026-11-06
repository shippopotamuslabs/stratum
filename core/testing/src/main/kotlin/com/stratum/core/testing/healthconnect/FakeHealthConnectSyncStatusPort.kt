package com.stratum.core.testing.healthconnect

import com.stratum.application.healthconnect.HealthConnectSyncStatusPort
import com.stratum.domain.healthconnect.HealthFailureKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class FakeHealthConnectSyncStatusPort : HealthConnectSyncStatusPort {
    private val pendingError = MutableStateFlow<HealthFailureKind?>(null)

    val currentPendingError: HealthFailureKind?
        get() = pendingError.value

    /** When non-null, writes throw this error without mutating state. */
    var failure: Throwable? = null

    override fun observePendingSyncErrors(): Flow<HealthFailureKind> =
        pendingError.map { it }.distinctUntilChanged().filterNotNull()

    override suspend fun reportSyncError(kind: HealthFailureKind) {
        failure?.let { throw it }
        pendingError.value = kind
    }

    override suspend fun clearPendingSyncError() {
        failure?.let { throw it }
        pendingError.value = null
    }
}
