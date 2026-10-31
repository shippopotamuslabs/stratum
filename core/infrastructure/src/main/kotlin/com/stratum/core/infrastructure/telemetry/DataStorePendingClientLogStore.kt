package com.stratum.core.infrastructure.telemetry

import android.content.Context
import com.stratum.domain.telemetry.ClientLogEntry
import com.stratum.domain.telemetry.PendingClientLogStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * [PendingClientLogStore] on DataStore (STR-27 offline queue).
 *
 * Malformed entries fail closed (dropped on load) so one bad row cannot wedge
 * telemetry. Writes are capped to [PendingClientLogStore.MAX_PERSISTED_ENTRIES]
 * newest entries; [com.stratum.domain.telemetry.ClientLogBuffer] already caps
 * what it hands over, this is defense in depth.
 */
@Singleton
class DataStorePendingClientLogStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PendingClientLogStore {
    override suspend fun loadPending(): List<ClientLogEntry> =
        context.pendingClientLogDataStore.data.first().entries.mapNotNull { it.toDomain() }

    override suspend fun savePending(entries: List<ClientLogEntry>) {
        context.pendingClientLogDataStore.updateData {
            PendingClientLogRecord(
                entries.takeLast(PendingClientLogStore.MAX_PERSISTED_ENTRIES).map { it.toRecord() },
            )
        }
    }
}
