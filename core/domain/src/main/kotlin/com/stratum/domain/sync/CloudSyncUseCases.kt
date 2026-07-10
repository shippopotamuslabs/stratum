package com.stratum.domain.sync

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveCloudSyncStatusUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    operator fun invoke(): StateFlow<SyncStatus> = cloudSyncPort.syncStatus
}

class StartCloudSyncUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    suspend operator fun invoke(userId: String): Result<Unit> = cloudSyncPort.start(userId)
}

class StopCloudSyncUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    suspend operator fun invoke() = cloudSyncPort.stop()
}

class RequestCloudSyncUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    suspend operator fun invoke(): Result<Unit> = cloudSyncPort.requestSync()
}
