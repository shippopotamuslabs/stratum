package com.stratum.core.infrastructure.telemetry

import com.stratum.domain.telemetry.ClientLogEntry
import com.stratum.domain.telemetry.ClientLogSeverity
import com.stratum.domain.telemetry.PendingClientLogStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStorePendingClientLogStore] and its mappers. The
 * typed DataStore file is process-scoped, so each test drains the queue first.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStorePendingClientLogStore")
class DataStorePendingClientLogStoreTest {

    private lateinit var store: DataStorePendingClientLogStore

    @BeforeEach
    fun setup() =
        runTest {
            store = DataStorePendingClientLogStore(RuntimeEnvironment.getApplication())
            store.savePending(emptyList())
        }

    private fun entry(message: String) =
        ClientLogEntry(
            severity = ClientLogSeverity.WARNING,
            tag = "Tag",
            message = message,
            clientTimestampMs = 1_000L,
            sessionId = "run-1",
            durationMs = 7L,
        )

    @Test
    @DisplayName("empty queue loads empty")
    fun emptyLoadsEmpty() =
        runTest {
            assertTrue(store.loadPending().isEmpty())
        }

    @Test
    @DisplayName("save then load round-trips entries with all fields")
    fun roundTrip() =
        runTest {
            val entries = listOf(entry("one"), entry("two").copy(severity = ClientLogSeverity.ERROR))

            store.savePending(entries)

            assertEquals(entries, store.loadPending())
        }

    @Test
    @DisplayName("save caps to the newest entries")
    fun saveCapsToNewest() =
        runTest {
            val entries = List(250) { entry("warn $it") }

            store.savePending(entries)

            val loaded = store.loadPending()
            assertEquals(PendingClientLogStore.MAX_PERSISTED_ENTRIES, loaded.size)
            assertEquals("warn 50", loaded.first().message)
            assertEquals("warn 249", loaded.last().message)
        }

    @Test
    @DisplayName("malformed rows fail closed on load")
    fun malformedRowsDropped() {
        assertNull(PendingClientLogEntryRecord(severity = "WARNING", tag = "", message = "m").toDomain())
        assertNull(PendingClientLogEntryRecord(severity = "WARNING", tag = "T", message = "").toDomain())
        assertNull(PendingClientLogEntryRecord(severity = "VERBOSE", tag = "T", message = "m").toDomain())
    }

    @Test
    @DisplayName("domain to record preserves severity name and optional fields")
    fun toRecordPreservesFields() {
        val record = entry("m").copy(sessionId = null, durationMs = null).toRecord()

        assertEquals(ClientLogSeverity.WARNING.name, record.severity)
        assertNull(record.sessionId)
        assertNull(record.durationMs)
    }

    @Test
    @DisplayName("OUTCOME round-trips the flat outcome columns")
    fun outcomeRoundTrip() =
        runTest {
            val outcomeEntry =
                ClientLogEntry(
                    severity = ClientLogSeverity.OUTCOME,
                    tag = "program_generation",
                    message = "outcome=expected_failure reason=synthesis_failed",
                    clientTimestampMs = 1_000L,
                    sessionId = "run-1",
                    durationMs = 77L,
                    outcome = "expected_failure",
                    reasonCode = "synthesis_failed",
                )

            store.savePending(listOf(outcomeEntry))

            assertEquals(listOf(outcomeEntry), store.loadPending())
        }
}
