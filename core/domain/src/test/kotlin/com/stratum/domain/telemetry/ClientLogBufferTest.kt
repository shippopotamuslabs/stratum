package com.stratum.domain.telemetry

import com.stratum.core.testing.telemetry.FakePendingClientLogStore
import com.stratum.core.testing.telemetry.FakeTelemetryUploadPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ClientLogBufferTest {
    private lateinit var upload: FakeTelemetryUploadPort
    private lateinit var store: FakePendingClientLogStore
    private var nowMs = START_MS

    @BeforeEach
    fun setup() {
        upload = FakeTelemetryUploadPort()
        store = FakePendingClientLogStore()
        nowMs = START_MS
    }

    private fun policy() =
        ClientLogUploadPolicy(
            maxBatchSize = 3,
            flushIntervalMs = 60_000L,
            maxUploadsPerHour = 10,
            maxBufferedEntries = 5,
            maxPersistedEntries = 4,
            maxMessageLength = 20,
            maxTagLength = 8,
            minRetryIntervalMs = 0L,
        )

    @Nested
    @DisplayName("flush")
    inner class Flush {
        @Test
        @DisplayName("uploads buffered entries with envelope and session correlation")
        fun uploadsWithEnvelope() =
            runTest {
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy(),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                buffer.record(ClientLogSeverity.WARNING, "SomeLongTag", "warn one")
                buffer.record(ClientLogSeverity.ERROR, "Tag", "error two", durationMs = 42L)

                buffer.flush()

                assertEquals(1, upload.uploadedBatches.size)
                val batch = upload.uploadedBatches.single()
                assertEquals(2, batch.entries.size)
                assertEquals(0, batch.droppedEntryCount)
                assertEquals(APP_VERSION, batch.appVersion)
                assertEquals(ClientLogBatch.PLATFORM_ANDROID, batch.platform)
                assertTrue(batch.entries.all { it.sessionId == buffer.sessionId })
                assertEquals(42L, batch.entries[1].durationMs)

                buffer.flush()
                assertEquals(1, upload.uploadedBatches.size)
            }

        @Test
        @DisplayName("reaching maxBatchSize triggers an automatic flush")
        fun autoFlushesAtThreshold() =
            runTest {
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy(),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                repeat(3) { buffer.record(ClientLogSeverity.WARNING, "Tag", "warn $it") }

                testScheduler.runCurrent()

                assertEquals(1, upload.uploadedBatches.size)
                assertEquals(3, upload.uploadedBatches.single().entries.size)
            }

        @Test
        @DisplayName("overflow drops oldest-first and reports the loss once")
        fun overflowDropsOldest() =
            runTest {
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy().copy(maxBatchSize = 100),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                repeat(7) { buffer.record(ClientLogSeverity.WARNING, "Tag", "warn $it") }

                buffer.flush()

                val batch = upload.uploadedBatches.single()
                assertEquals(5, batch.entries.size)
                assertEquals("warn 2", batch.entries.first().message)
                assertEquals(2, batch.droppedEntryCount)

                buffer.record(ClientLogSeverity.WARNING, "Tag", "after")
                buffer.flush()
                val followUp = upload.uploadedBatches.last()
                assertEquals(listOf("after"), followUp.entries.map { it.message })
                assertEquals(0, followUp.droppedEntryCount)
            }

        @Test
        @DisplayName("hourly cap defers upload, persists pending, then resumes next hour")
        fun hourlyCapDefers() =
            runTest {
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy().copy(maxUploadsPerHour = 1),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                buffer.record(ClientLogSeverity.WARNING, "Tag", "first")
                buffer.flush()
                assertEquals(1, upload.uploadedBatches.size)

                buffer.record(ClientLogSeverity.WARNING, "Tag", "second")
                buffer.flush()
                assertEquals(1, upload.uploadedBatches.size)
                assertEquals(listOf("second"), store.savedSnapshots.last().map { it.message })

                nowMs += 3_600_001L
                buffer.flush()
                assertEquals(2, upload.uploadedBatches.size)
            }

        @Test
        @DisplayName("failure persists pending and backs off until the retry interval passes")
        fun failurePersistsAndBacksOff() =
            runTest {
                upload.shouldFail = true
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy().copy(minRetryIntervalMs = 30_000L),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                buffer.record(ClientLogSeverity.WARNING, "Tag", "offline warn")

                buffer.flush()
                buffer.flush()

                assertEquals(1, upload.uploadedBatches.size)
                assertEquals(listOf("offline warn"), store.savedSnapshots.last().map { it.message })

                nowMs += 30_001L
                upload.shouldFail = false
                buffer.flush()
                assertEquals(2, upload.uploadedBatches.size)
                assertTrue(store.savedSnapshots.last().isEmpty())
            }

        @Test
        @DisplayName("truncates tag and message and coerces negative durations")
        fun truncatesAndCoerces() =
            runTest {
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy(),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                buffer.record(
                    ClientLogSeverity.WARNING,
                    "AVeryLongTagName",
                    "a message far too long for the cap",
                    durationMs = -5L,
                )

                buffer.flush()

                val entry = upload.uploadedBatches.single().entries.single()
                assertEquals(8, entry.tag.length)
                assertEquals(20, entry.message.length)
                assertEquals(0L, entry.durationMs)
            }
    }

    @Nested
    @DisplayName("offline queue")
    inner class OfflineQueue {
        private fun entry(message: String) =
            ClientLogEntry(
                severity = ClientLogSeverity.WARNING,
                tag = "Tag",
                message = message,
                clientTimestampMs = nowMs,
                sessionId = "previous-run",
            )

        @Test
        @DisplayName("restorePending prepends stored entries oldest-first")
        fun restoresInOrder() =
            runTest {
                store.pending = listOf(entry("pending-one"), entry("pending-two"))
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy(),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                buffer.record(ClientLogSeverity.WARNING, "Tag", "fresh")

                buffer.restorePending()
                buffer.flush()

                assertEquals(
                    listOf("pending-one", "pending-two", "fresh"),
                    upload.uploadedBatches.single().entries.map { it.message },
                )
            }

        @Test
        @DisplayName("start restores pending and flushes on the interval")
        fun startFlushesPeriodically() =
            runTest {
                store.pending = listOf(entry("queued"))
                val buffer =
                    ClientLogBuffer(
                        scope = backgroundScope,
                        upload = upload,
                        pendingStore = store,
                        policy = policy(),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                buffer.start()
                runCurrent()
                assertTrue(upload.uploadedBatches.isEmpty())

                advanceTimeBy(60_000L)
                runCurrent()

                assertEquals(
                    listOf("queued"),
                    upload.uploadedBatches.single().entries.map { it.message },
                )
            }

        @Test
        @DisplayName("store failures never break recording or flushing")
        fun storeFailuresAreBestEffort() =
            runTest {
                store.failOnLoad = true
                store.failOnSave = true
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy(),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                buffer.record(ClientLogSeverity.WARNING, "Tag", "warn")

                buffer.restorePending()
                buffer.flush()

                assertEquals(1, upload.uploadedBatches.size)
            }
    }

    @Nested
    @DisplayName("blank guard")
    inner class BlankGuard {
        @Test
        @DisplayName("blank tags and messages are dropped before enqueue and never uploaded")
        fun blankEntriesNeverReachUpload() =
            runTest {
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = upload,
                        pendingStore = store,
                        policy = policy(),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                buffer.record(ClientLogSeverity.WARNING, "", "has message")
                buffer.record(ClientLogSeverity.WARNING, "   ", "whitespace tag")
                buffer.record(ClientLogSeverity.WARNING, "Tag", "")
                buffer.record(ClientLogSeverity.WARNING, "Tag", "   ")

                buffer.flush()

                assertTrue(upload.uploadedBatches.isEmpty())

                buffer.record(ClientLogSeverity.WARNING, "Tag", "valid")
                buffer.flush()

                assertEquals(
                    listOf("valid"),
                    upload.uploadedBatches.single().entries.map { it.message },
                )
            }
    }

    @Nested
    @DisplayName("concurrent record during upload")
    inner class ConcurrentFlush {
        @Test
        @DisplayName("racing overflow removes only uploaded entries and preserves the drop count")
        fun racingOverflowKeepsNewEntriesAndDropCount() =
            runTest {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val uploaded = mutableListOf<ClientLogBatch>()
                val racingUpload =
                    object : TelemetryUploadPort {
                        override suspend fun upload(batch: ClientLogBatch): Result<Unit> {
                            uploaded += batch
                            started.complete(Unit)
                            release.await()
                            return Result.success(Unit)
                        }
                    }
                val buffer =
                    ClientLogBuffer(
                        scope = this,
                        upload = racingUpload,
                        pendingStore = store,
                        policy = policy().copy(maxBatchSize = 100),
                        clock = { nowMs },
                        appVersion = APP_VERSION,
                    )
                repeat(5) { buffer.record(ClientLogSeverity.WARNING, "Tag", "m$it") }

                val flushJob = launch { buffer.flush() }
                started.await()
                // Overflow while the upload is in flight: drops m0-m2, keeps m3-m4.
                repeat(3) { buffer.record(ClientLogSeverity.WARNING, "Tag", "n$it") }
                release.complete(Unit)
                flushJob.join()

                assertEquals(
                    listOf("m0", "m1", "m2", "m3", "m4"),
                    uploaded.single().entries.map { it.message },
                )

                buffer.flush()

                // m3-m4 were uploaded (removed once); n0-n2 were never uploaded
                // (kept); the 3 overflow drops during the first upload are still
                // reported instead of reset to zero.
                assertEquals(2, uploaded.size)
                assertEquals(listOf("n0", "n1", "n2"), uploaded[1].entries.map { it.message })
                assertEquals(3, uploaded[1].droppedEntryCount)
            }
    }

    companion object {
        const val APP_VERSION = "test-1"
        const val START_MS = 1_000_000L
    }
}
