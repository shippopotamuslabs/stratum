package com.stratum.core.testing.sync

import com.stratum.domain.sync.CloudSyncPort
import com.stratum.domain.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [CloudSyncPort] for unit tests. Records start/stop/request calls without network.
 */
class FakeCloudSyncPort : CloudSyncPort {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    var startedUserId: String? = null
        private set
    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set
    var requestCount: Int = 0
        private set

    var startResult: Result<Unit> = Result.success(Unit)
    var requestResult: Result<Unit> = Result.success(Unit)

    fun setStatus(status: SyncStatus) {
        _syncStatus.value = status
    }

    override suspend fun start(userId: String): Result<Unit> {
        startCount++
        startedUserId = userId
        _syncStatus.value = SyncStatus.Syncing
        return startResult.also {
            if (it.isSuccess) _syncStatus.value = SyncStatus.Idle
            else _syncStatus.value = SyncStatus.Error(it.exceptionOrNull()?.message ?: "start failed")
        }
    }

    override suspend fun stop() {
        stopCount++
        startedUserId = null
        _syncStatus.value = SyncStatus.Idle
    }

    override suspend fun requestSync(): Result<Unit> {
        requestCount++
        _syncStatus.value = SyncStatus.Syncing
        return requestResult.also {
            if (it.isSuccess) _syncStatus.value = SyncStatus.Idle
            else _syncStatus.value = SyncStatus.Error(it.exceptionOrNull()?.message ?: "request failed")
        }
    }
}
