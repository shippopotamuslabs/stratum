package com.stratum.core.testing.telemetry

import com.stratum.core.infrastructure.telemetry.ClientLogEntry
import com.stratum.core.infrastructure.telemetry.PendingClientLogStore

/**
 * In-memory [PendingClientLogStore] for unit tests. Seed via [pending];
 * every save is recorded in [savedSnapshots].
 */
class FakePendingClientLogStore : PendingClientLogStore {
    var pending: List<ClientLogEntry> = emptyList()
    var failOnLoad: Boolean = false
    var failOnSave: Boolean = false

    /** Exact instance thrown from [loadPending] when set; takes precedence over [failOnLoad]. */
    var loadFailure: Throwable? = null

    /** Exact instance thrown from [savePending] when set; takes precedence over [failOnSave]. */
    var saveFailure: Throwable? = null
    val savedSnapshots = mutableListOf<List<ClientLogEntry>>()

    override suspend fun loadPending(): List<ClientLogEntry> {
        loadFailure?.let { throw it }
        if (failOnLoad) {
            throw RuntimeException("pending load failed")
        }
        return pending
    }

    override suspend fun savePending(entries: List<ClientLogEntry>) {
        saveFailure?.let { throw it }
        if (failOnSave) {
            throw RuntimeException("pending save failed")
        }
        savedSnapshots += entries
    }

    fun clear() {
        pending = emptyList()
        failOnLoad = false
        failOnSave = false
        loadFailure = null
        saveFailure = null
        savedSnapshots.clear()
    }
}
