package com.stratum.core.testing.sync

import com.stratum.domain.sync.CloudSessionState
import com.stratum.application.sync.CloudSyncDiagnostic
import com.stratum.application.sync.CloudSyncOutcome
import com.stratum.application.sync.CloudSyncPort
import com.stratum.domain.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [CloudSyncPort] for unit tests. Records start/stop/request/wipe calls without network.
 */
class FakeCloudSyncPort : CloudSyncPort {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _sessionState =
        MutableStateFlow<CloudSessionState>(CloudSessionState.Inactive)
    override val sessionState: StateFlow<CloudSessionState> = _sessionState.asStateFlow()

    var startedUserId: String? = null
        private set
    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set
    var requestCount: Int = 0
        private set
    var wipeCount: Int = 0
        private set
    var lastWipedUserId: String? = null
        private set

    /**
     * When non-null, [start] returns a Failed outcome carrying this as its
     * diagnostic cause instead of succeeding. A [kotlinx.coroutines.CancellationException]
     * still throws — cancellation is never an outcome branch.
     */
    var startFailure: Throwable? = null
    /** Same contract as [startFailure], for [requestSync]. */
    var requestFailure: Throwable? = null
    /** Same contract as [startFailure], for [endSessionAndWipe]. */
    var wipeFailure: Throwable? = null

    private fun failureOutcome(failure: Throwable): CloudSyncOutcome {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        return CloudSyncOutcome.Failed(listOf(CloudSyncDiagnostic.OperationFailed(failure)))
    }

    /** Call order log: "start", "stop", "request", "wipe". */
    val callOrder: MutableList<String> = mutableListOf()

    fun setStatus(status: SyncStatus) {
        _syncStatus.value = status
    }

    fun setSessionState(state: CloudSessionState) {
        _sessionState.value = state
    }

    override suspend fun start(userId: String): CloudSyncOutcome {
        startCount++
        callOrder += "start"
        startedUserId = userId
        _sessionState.value = CloudSessionState.Establishing(userId)
        _syncStatus.value = SyncStatus.Syncing
        val failure = startFailure
        if (failure != null) {
            val message = failure.message ?: "start failed"
            _syncStatus.value = SyncStatus.Error(message)
            _sessionState.value = CloudSessionState.Failed(userId, message)
            return failureOutcome(failure)
        }
        _syncStatus.value = SyncStatus.Idle
        _sessionState.value = CloudSessionState.Ready(userId)
        return CloudSyncOutcome.Success
    }

    override suspend fun stop() {
        stopCount++
        callOrder += "stop"
        startedUserId = null
        _sessionState.value = CloudSessionState.Inactive
        _syncStatus.value = SyncStatus.Idle
    }

    override suspend fun requestSync(): CloudSyncOutcome {
        requestCount++
        callOrder += "request"
        _syncStatus.value = SyncStatus.Syncing
        val failure = requestFailure
        if (failure != null) {
            _syncStatus.value = SyncStatus.Error(failure.message ?: "request failed")
            return failureOutcome(failure)
        }
        _syncStatus.value = SyncStatus.Idle
        val uid = startedUserId
        if (uid != null) {
            _sessionState.value = CloudSessionState.Ready(uid)
        }
        return CloudSyncOutcome.Success
    }

    override suspend fun endSessionAndWipe(userId: String): CloudSyncOutcome {
        wipeCount++
        callOrder += "wipe"
        lastWipedUserId = userId
        startedUserId = null
        _sessionState.value = CloudSessionState.Inactive
        _syncStatus.value = SyncStatus.Idle
        val failure = wipeFailure ?: return CloudSyncOutcome.Success
        return failureOutcome(failure)
    }
}
