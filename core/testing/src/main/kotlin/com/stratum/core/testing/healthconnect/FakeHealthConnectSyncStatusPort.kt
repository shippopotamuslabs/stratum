package com.stratum.core.testing.healthconnect

import com.stratum.application.healthconnect.HealthConnectSyncStatusPort
import com.stratum.application.healthconnect.HealthConnectWriteOutcome
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import com.stratum.domain.healthconnect.HealthFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class FakeHealthConnectSyncStatusPort : HealthConnectSyncStatusPort {
    private val pendingError = MutableStateFlow<HealthFailureKind?>(null)

    val currentPendingError: HealthFailureKind?
        get() = pendingError.value

    /**
     * When non-null, writes return a Failed outcome carrying this as the
     * diagnostic cause without mutating state. A [CancellationException]
     * still throws — cancellation is never an outcome branch.
     */
    var failure: Throwable? = null

    override fun observePendingSyncErrors(): Flow<HealthFailureKind> =
        pendingError.map { it }.distinctUntilChanged().filterNotNull()

    override suspend fun reportSyncError(
        kind: HealthFailureKind,
    ): HealthConnectWriteOutcome {
        failure?.let { return failureOutcome(it) }
        pendingError.value = kind
        return HealthConnectWriteOutcome.Success
    }

    override suspend fun clearPendingSyncError(): HealthConnectWriteOutcome {
        failure?.let { return failureOutcome(it) }
        pendingError.value = null
        return HealthConnectWriteOutcome.Success
    }

    private fun failureOutcome(failure: Throwable): HealthConnectWriteOutcome {
        if (failure is CancellationException) throw failure
        return HealthConnectWriteOutcome.Failed(
            listOf(HealthSyncDiagnostic.OperationFailed(failure)),
        )
    }
}
