package com.stratum.core.testing.healthconnect

import com.stratum.application.healthconnect.HealthConnectSyncStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

class FakeHealthConnectSyncStatusPort : HealthConnectSyncStatusPort {
    private val pendingError = MutableStateFlow<String?>(null)

    val currentPendingError: String?
        get() = pendingError.value

    /** When non-null, writes fail with this error without mutating state. */
    var failure: Throwable? = null

    override fun observePendingSyncErrors(): Flow<String> =
        pendingError.mapNotNull { it }.distinctUntilChanged()

    override suspend fun reportSyncError(message: String): Result<Unit> {
        failure?.let { return Result.failure(it) }
        pendingError.value = message
        return Result.success(Unit)
    }

    override suspend fun clearPendingSyncError(): Result<Unit> {
        failure?.let { return Result.failure(it) }
        pendingError.value = null
        return Result.success(Unit)
    }
}
