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

    /** When non-null, writes fail with this error without mutating state. */
    var failure: Throwable? = null

    override fun observePendingSyncErrors(): Flow<HealthFailureKind> =
        pendingError.map { it }.distinctUntilChanged().filterNotNull()

    override suspend fun reportSyncError(kind: HealthFailureKind): Result<Unit> {
        failure?.let { return Result.failure(it) }
        pendingError.value = kind
        return Result.success(Unit)
    }

    override suspend fun clearPendingSyncError(): Result<Unit> {
        failure?.let { return Result.failure(it) }
        pendingError.value = null
        return Result.success(Unit)
    }
}
