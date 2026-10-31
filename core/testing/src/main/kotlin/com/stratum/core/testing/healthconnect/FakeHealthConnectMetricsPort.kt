package com.stratum.core.testing.healthconnect

import com.stratum.application.healthconnect.HealthConnectMetricsPort
import com.stratum.domain.health.HealthMetricType
import com.stratum.domain.health.HealthMetricsRequest
import com.stratum.domain.health.HealthMetricsSnapshot
import kotlinx.coroutines.CompletableDeferred

class FakeHealthConnectMetricsPort : HealthConnectMetricsPort {
    var available: Boolean = true
    var granted: Set<HealthMetricType> = emptySet()
    var snapshot: HealthMetricsSnapshot = HealthMetricsSnapshot()
    var lastRequest: HealthMetricsRequest? = null
    var snapshotGate: CompletableDeferred<Unit>? = null
    var readSnapshotCount: Int = 0
    var readSnapshotFailure: Throwable? = null

    override suspend fun isAvailable(): Boolean = available

    override suspend fun grantedReadTypes(): Set<HealthMetricType> = granted

    override suspend fun readSnapshot(request: HealthMetricsRequest): HealthMetricsSnapshot {
        snapshotGate?.await()
        lastRequest = request
        readSnapshotCount += 1
        readSnapshotFailure?.let { throw it }
        return snapshot.copy(grantedTypes = granted)
    }
}
