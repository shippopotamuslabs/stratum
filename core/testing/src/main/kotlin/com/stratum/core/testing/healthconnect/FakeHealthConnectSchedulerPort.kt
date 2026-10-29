package com.stratum.core.testing.healthconnect

import com.stratum.application.healthconnect.HealthConnectSchedulerPort
import com.stratum.domain.training.identity.TrainingSessionId

class FakeHealthConnectSchedulerPort : HealthConnectSchedulerPort {
    val enqueuedSessionIds = mutableListOf<TrainingSessionId>()
    var throwOnEnqueue: Throwable? = null

    override suspend fun enqueueSync(sessionId: TrainingSessionId) {
        throwOnEnqueue?.let { throw it }
        enqueuedSessionIds.add(sessionId)
    }
}
