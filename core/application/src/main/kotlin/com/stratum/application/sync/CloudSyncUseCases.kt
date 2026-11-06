package com.stratum.application.sync

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.StateFlow
import com.stratum.domain.sync.CloudSessionState
import com.stratum.domain.sync.SyncStatus

/**
 * Outcome of a cloud sync operation (STR-243).
 *
 * The port adapter logs an operational failure once at its own boundary before
 * throwing; the use case translates the throw into [Failed], which carries NO
 * payload: [Failed] wraps the operation's total failure, so the throwable
 * itself must not ride on it under a diagnostics name. The adapter's own log
 * is the failure's single error record. Coroutine cancellation is not a
 * branch: it propagates as cancellation.
 */
sealed interface CloudSyncOutcome {
    data object Success : CloudSyncOutcome

    data object Failed : CloudSyncOutcome
}

/**
 * A sync operation failure recorded as data (§10 diagnostics channel). The
 * boundary drains each diagnostic through `LoggingPort.error` with the
 * throwable attached; diagnostics never reach UI state.
 *
 * Produced only for SECONDARY failures: the compensating sync restart after a
 * logout / account-deletion failure (see
 * [StartCloudSyncUseCase.restartDiagnostics]) — never for an operation's own
 * total failure, which is [CloudSyncOutcome.Failed].
 */
sealed interface CloudSyncDiagnostic {
    data class OperationFailed(
        val cause: Throwable,
    ) : CloudSyncDiagnostic
}

private suspend fun cloudSyncOutcome(block: suspend () -> Unit): CloudSyncOutcome =
    try {
        block()
        CloudSyncOutcome.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        CloudSyncOutcome.Failed
    }

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
        cloudSyncOutcome { cloudSyncPort.start(userId) }

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
        try {
            cloudSyncPort.start(userId)
            emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            listOf(CloudSyncDiagnostic.OperationFailed(error))
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
        cloudSyncOutcome { cloudSyncPort.requestSync() }
}

class EndSessionAndWipeUseCase @Inject constructor(
    private val cloudSyncPort: CloudSyncPort,
) {
    suspend operator fun invoke(userId: String): CloudSyncOutcome =
        cloudSyncOutcome { cloudSyncPort.endSessionAndWipe(userId) }
}
