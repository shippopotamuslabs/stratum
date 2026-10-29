package com.stratum.domain.telemetry

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.stratum.domain.logging.LoggingPort

/**
 * Client-side volume/cost controls for tier-2 telemetry (STR-27).
 *
 * Worst case per device: [ClientLogUploadPolicy.maxUploadsPerHour] batches/hour
 * of [ClientLogUploadPolicy.maxBatchSize] entries, so a chatty warning cannot
 * blow up the BigQuery bill. Overflow drops oldest-first and reports the loss
 * via [ClientLogBatch.droppedEntryCount] so under-counting is visible in SQL.
 */
data class ClientLogUploadPolicy(
    val maxBatchSize: Int = 50,
    val flushIntervalMs: Long = 60_000L,
    val maxUploadsPerHour: Int = 10,
    val maxBufferedEntries: Int = 500,
    val maxPersistedEntries: Int = PendingClientLogStore.MAX_PERSISTED_ENTRIES,
    val maxMessageLength: Int = 4000,
    val maxTagLength: Int = 64,
    val minRetryIntervalMs: Long = 30_000L,
)

/**
 * Batching in-memory buffer with offline queueing for [ClientLogEntry].
 *
 * Thread-safe: [record] is synchronous and callable from any thread (it backs
 * [LoggingPort][com.stratum.domain.logging.LoggingPort] methods, which are
 * non-suspending). Uploads run on [scope]; unsent entries survive process
 * death via [pendingStore]. [sessionId] correlates every entry from one
 * process run in SQL.
 */
class ClientLogBuffer(
    private val scope: CoroutineScope,
    private val upload: TelemetryUploadPort,
    private val pendingStore: PendingClientLogStore,
    private val policy: ClientLogUploadPolicy = ClientLogUploadPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val appVersion: String = ClientLogBatch.APP_VERSION_UNKNOWN,
    val sessionId: String = UUID.randomUUID().toString(),
) {
    private val lock = Any()
    private val buffer = ArrayDeque<ClientLogEntry>()
    private val flushMutex = Mutex()
    private var droppedEntryCount = 0
    private val flushTimesMs = ArrayDeque<Long>()
    private var lastFailureMs = 0L
    private var tickerStarted = false

    fun record(
        severity: ClientLogSeverity,
        tag: String,
        message: String,
        durationMs: Long? = null,
    ) {
        val truncatedTag = tag.take(policy.maxTagLength)
        val truncatedMessage = message.take(policy.maxMessageLength)
        if (truncatedTag.isBlank() || truncatedMessage.isBlank()) {
            // The server rejects the whole batch on one empty tag/message, so a
            // blank (or fully-scrubbed) entry would poison every retry. Drop it
            // here; the caller already logged it locally before recording.
            return
        }
        val entry =
            ClientLogEntry(
                severity = severity,
                tag = truncatedTag,
                message = truncatedMessage,
                clientTimestampMs = clock(),
                sessionId = sessionId,
                durationMs = durationMs?.coerceAtLeast(0L),
            )
        val shouldFlush =
            synchronized(lock) {
                if (buffer.size >= policy.maxBufferedEntries) {
                    buffer.removeFirst()
                    droppedEntryCount++
                }
                buffer.addLast(entry)
                buffer.size >= policy.maxBatchSize
            }
        if (shouldFlush) {
            scope.launch { flush() }
        }
    }

    /** Restores the offline queue once, then flushes on [ClientLogUploadPolicy.flushIntervalMs]. */
    fun start() {
        val firstStart =
            synchronized(lock) {
                if (tickerStarted) {
                    false
                } else {
                    tickerStarted = true
                    true
                }
            }
        if (!firstStart) {
            return
        }
        scope.launch {
            restorePending()
            while (true) {
                delay(policy.flushIntervalMs)
                flush()
            }
        }
    }

    suspend fun restorePending() {
        val pending =
            try {
                pendingStore.loadPending()
            } catch (_: Exception) {
                return
            }
        if (pending.isEmpty()) {
            return
        }
        synchronized(lock) {
            val room = (policy.maxBufferedEntries - buffer.size).coerceAtLeast(0)
            pending.takeLast(room).reversed().forEach { buffer.addFirst(it) }
        }
    }

    suspend fun flush() {
        flushMutex.withLock { flushLocked() }
    }

    private suspend fun flushLocked() {
        val nowMs = clock()
        flushTimesMs.removeAll { it <= nowMs - HOUR_MS }
        val snapshot = synchronized(lock) { buffer.toList() }
        if (snapshot.isEmpty()) {
            return
        }
        if (nowMs - lastFailureMs < policy.minRetryIntervalMs || flushTimesMs.size >= policy.maxUploadsPerHour) {
            persist(snapshot)
            return
        }
        val batchEntries = snapshot.take(policy.maxBatchSize)
        val droppedAtStart = synchronized(lock) { droppedEntryCount }
        val result =
            upload.upload(
                ClientLogBatch(
                    entries = batchEntries,
                    droppedEntryCount = droppedAtStart,
                    appVersion = appVersion,
                ),
            )
        if (result.isSuccess) {
            synchronized(lock) {
                // A concurrent record() may have overflowed and dropped from the
                // same front during upload, so only remove entries that were
                // actually uploaded: fast path when the prefix still matches,
                // per-entry removal when it shifted underneath the snapshot.
                if (buffer.size >= batchEntries.size &&
                    batchEntries.indices.all { buffer[it] == batchEntries[it] }
                ) {
                    repeat(batchEntries.size) { buffer.removeFirst() }
                } else {
                    batchEntries.forEach { buffer.remove(it) }
                }
                // Preserve overflows that happened during the upload; only the
                // count already reported in this batch is cleared.
                droppedEntryCount = (droppedEntryCount - droppedAtStart).coerceAtLeast(0)
            }
            flushTimesMs.addLast(nowMs)
            persist(synchronized(lock) { buffer.toList() })
        } else {
            lastFailureMs = nowMs
            persist(snapshot)
        }
    }

    private suspend fun persist(entries: List<ClientLogEntry>) {
        try {
            pendingStore.savePending(entries.takeLast(policy.maxPersistedEntries))
        } catch (_: Exception) {
            // Offline queue is best-effort; the in-memory buffer still retries.
        }
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
    }
}
