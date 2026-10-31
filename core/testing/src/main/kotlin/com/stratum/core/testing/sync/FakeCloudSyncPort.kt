package com.stratum.core.testing.sync

import com.stratum.domain.sync.CloudSessionState
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

    var startResult: Result<Unit> = Result.success(Unit)
    /** When non-null, [start] throws this instead of returning [startResult]. */
    var startThrowable: Throwable? = null
    var requestResult: Result<Unit> = Result.success(Unit)
    var wipeResult: Result<Unit> = Result.success(Unit)

    /** Call order log: "start", "stop", "request", "wipe". */
    val callOrder: MutableList<String> = mutableListOf()

    fun setStatus(status: SyncStatus) {
        _syncStatus.value = status
    }

    fun setSessionState(state: CloudSessionState) {
        _sessionState.value = state
    }

    override suspend fun start(userId: String): Result<Unit> {
        startCount++
        callOrder += "start"
        startedUserId = userId
        startThrowable?.let { throw it }
        _sessionState.value = CloudSessionState.Establishing(userId)
        _syncStatus.value = SyncStatus.Syncing
        return startResult.also {
            if (it.isSuccess) {
                _syncStatus.value = SyncStatus.Idle
                _sessionState.value = CloudSessionState.Ready(userId)
            } else {
                val message = it.exceptionOrNull()?.message ?: "start failed"
                _syncStatus.value = SyncStatus.Error(message)
                _sessionState.value = CloudSessionState.Failed(userId, message)
            }
        }
    }

    override suspend fun stop() {
        stopCount++
        callOrder += "stop"
        startedUserId = null
        _sessionState.value = CloudSessionState.Inactive
        _syncStatus.value = SyncStatus.Idle
    }

    override suspend fun requestSync(): Result<Unit> {
        requestCount++
        callOrder += "request"
        _syncStatus.value = SyncStatus.Syncing
        return requestResult.also {
            if (it.isSuccess) {
                _syncStatus.value = SyncStatus.Idle
                val uid = startedUserId
                if (uid != null) {
                    _sessionState.value = CloudSessionState.Ready(uid)
                }
            } else {
                _syncStatus.value =
                    SyncStatus.Error(it.exceptionOrNull()?.message ?: "request failed")
            }
        }
    }

    override suspend fun endSessionAndWipe(userId: String): Result<Unit> {
        wipeCount++
        callOrder += "wipe"
        lastWipedUserId = userId
        startedUserId = null
        _sessionState.value = CloudSessionState.Inactive
        _syncStatus.value = SyncStatus.Idle
        return wipeResult
    }
}
