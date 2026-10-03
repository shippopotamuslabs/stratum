package com.stratum.core.testing.telemetry

import com.stratum.domain.telemetry.ClientLogEntry
import com.stratum.domain.telemetry.PendingClientLogStore

/**
 * In-memory [PendingClientLogStore] for unit tests. Seed via [pending];
 * every save is recorded in [savedSnapshots].
 */
class FakePendingClientLogStore : PendingClientLogStore {
    var pending: List<ClientLogEntry> = emptyList()
    var failOnLoad: Boolean = false
    var failOnSave: Boolean = false
    val savedSnapshots = mutableListOf<List<ClientLogEntry>>()

    override suspend fun loadPending(): List<ClientLogEntry> {
        if (failOnLoad) {
            throw RuntimeException("pending load failed")
        }
        return pending
    }

    override suspend fun savePending(entries: List<ClientLogEntry>) {
        if (failOnSave) {
            throw RuntimeException("pending save failed")
        }
        savedSnapshots += entries
    }

    fun clear() {
        pending = emptyList()
        failOnLoad = false
        failOnSave = false
        savedSnapshots.clear()
    }
}
