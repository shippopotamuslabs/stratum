package com.stratum.core.testing.healthconnect

import com.stratum.domain.healthconnect.HealthConnectMetricsPort
import com.stratum.domain.healthconnect.HealthMetricType
import com.stratum.domain.healthconnect.HealthMetricsRequest
import com.stratum.domain.healthconnect.HealthMetricsSnapshot
import kotlinx.coroutines.CompletableDeferred

class FakeHealthConnectMetricsPort : HealthConnectMetricsPort {
    var available: Boolean = true
    var granted: Set<HealthMetricType> = emptySet()
    var snapshot: HealthMetricsSnapshot = HealthMetricsSnapshot()
    var lastRequest: HealthMetricsRequest? = null
    var snapshotGate: CompletableDeferred<Unit>? = null
    var readSnapshotCount: Int = 0

    override suspend fun isAvailable(): Boolean = available

    override suspend fun grantedReadTypes(): Set<HealthMetricType> = granted

    override suspend fun readSnapshot(request: HealthMetricsRequest): HealthMetricsSnapshot {
        snapshotGate?.await()
        lastRequest = request
        readSnapshotCount += 1
        return snapshot.copy(grantedTypes = granted)
    }
}
