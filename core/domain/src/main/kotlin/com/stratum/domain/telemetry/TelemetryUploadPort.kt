package com.stratum.domain.telemetry

/**
 * Network boundary for tier-2 client logs. Implemented in `:core:inference`
 * via the `ingestClientLogs` callable; faked in tests.
 */
interface TelemetryUploadPort {
    suspend fun upload(batch: ClientLogBatch): Result<Unit>
}

/**
 * Durable offline queue for not-yet-uploaded entries. Implemented in
 * `:core:data` on DataStore — never Room, so telemetry needs no schema
 * version bump (schema lock, ARCHITECTURE.md §6).
 */
interface PendingClientLogStore {
    suspend fun loadPending(): List<ClientLogEntry>

    suspend fun savePending(entries: List<ClientLogEntry>)
}
