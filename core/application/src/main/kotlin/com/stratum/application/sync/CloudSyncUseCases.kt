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
    suspend operator fun invoke(userId: String): CloudSyncOutcome =
        cloudSyncPort.start(userId)

    /**
     * Runs the compensating sync restart after a logout / account-deletion
     * failure left sync stopped for a still-authenticated user (STR-256), and
     * returns the restart's failure as §10 [CloudSyncDiagnostic]s (empty on
     * success) for the terminal boundary's exactly-once drain. The restart
     * failure is secondary to the primary logout/deletion outcome, so it
     * legitimately rides the diagnostics channel; the primary outcome branch
     * is never retyped by it. [CancellationException] from the restart
     * propagates, never rides as a diagnostic.
     */
    suspend fun restartDiagnostics(userId: String): List<CloudSyncDiagnostic> =
        when (val outcome = cloudSyncPort.start(userId)) {
            CloudSyncOutcome.Success -> emptyList()
            is CloudSyncOutcome.Failed -> outcome.diagnostics
        }
}

class StopCloudSyncUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    suspend operator fun invoke() = cloudSyncPort.stop()
}

class RequestCloudSyncUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    suspend operator fun invoke(): CloudSyncOutcome =
        cloudSyncPort.requestSync()
}

class EndSessionAndWipeUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    suspend operator fun invoke(userId: String): CloudSyncOutcome =
        cloudSyncPort.endSessionAndWipe(userId)
}
