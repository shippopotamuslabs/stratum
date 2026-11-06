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
 * throwing; the use case translates the throw into [Failed], whose
 * [CloudSyncDiagnostic]s carry the originating throwable to the terminal
 * boundary (auth listener, account-lifecycle ViewModels) that drains it
 * exactly once through its own log wiring. Coroutine cancellation is not a
 * branch: it propagates as cancellation.
 */
sealed interface CloudSyncOutcome {
    data object Success : CloudSyncOutcome

    data class Failed(
        val diagnostics: List<CloudSyncDiagnostic>,
    ) : CloudSyncOutcome
}

/**
 * A sync operation failure recorded as data (§10 diagnostics channel). The
 * boundary drains each diagnostic through `LoggingPort.error` with the
 * throwable attached; diagnostics never reach UI state.
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
    } catch (error: Throwable) {
        CloudSyncOutcome.Failed(listOf(CloudSyncDiagnostic.OperationFailed(error)))
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
