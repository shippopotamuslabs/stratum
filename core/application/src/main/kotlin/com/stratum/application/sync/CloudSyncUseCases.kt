package com.stratum.application.sync

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.stratum.domain.sync.CloudSessionState
import com.stratum.domain.sync.SyncStatus

class ObserveCloudSyncStatusUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    operator fun invoke(): StateFlow<SyncStatus> = cloudSyncPort.syncStatus
}

class ObserveCloudSessionStateUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    operator fun invoke(): StateFlow<CloudSessionState> = cloudSyncPort.sessionState
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

class EndSessionAndWipeUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    suspend operator fun invoke(userId: String): Result<Unit> =
        cloudSyncPort.endSessionAndWipe(userId)
}
