package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.data.db.ProgramChangeEventEntity
import com.stratum.core.data.db.ProgramEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.sync.testSyncTouch
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * [RoomProgramChangeEventRepository] skip-logging tests (STR-260). Runs with Robolectric
 * so a real in-memory [StratumDatabase] backs the DAO.
 */
@ExtendWith(RobolectricExtension::class)
class RoomProgramChangeEventRepositoryTest {
    private lateinit var database: StratumDatabase
    private lateinit var fakeLogging: FakeLoggingPort
    private lateinit var repository: RoomProgramChangeEventRepository

    private val programId = ProgramId("prog-1")

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        fakeLogging = FakeLoggingPort()
        repository = RoomProgramChangeEventRepository(
            dao = database.programChangeEventDao(),
            syncTouch = testSyncTouch(RuntimeEnvironment.getApplication(), database.pendingCloudDeleteDao()),
            loggingPort = fakeLogging,
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private suspend fun seedProgram() {
        database.programDao().upsert(
            ProgramEntity(
                id = programId,
                name = "Test Program",
                createdAt = "2025-01-01T00:00:00Z",
                cycleLength = 2,
            ),
        )
    }

    @Test
    @DisplayName("undecodable row is skipped and logged via LoggingPort with identical tag and message")
    fun undecodableRowSkippedAndLogged() = runTest {
        seedProgram()
        val good = ProgramChangeEvent(
            id = ProgramChangeEventId("evt-good"),
            programId = programId,
            eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
            occurredAt = Instant.ofEpochMilli(1000L),
            payload = ProgramChangeEventPayload.ProgramRegenerated,
        )
        repository.append(good)
        // Pre-v6 SCHEME_CHANGED row with no payload data: toDomain() returns null.
        database.programChangeEventDao().upsert(
            ProgramChangeEventEntity(
                id = ProgramChangeEventId("evt-bad"),
                programId = programId,
                eventType = ProgramChangeEventType.SCHEME_CHANGED,
                occurredAtEpochMillis = 2000L,
                payloadJson = null,
            ),
        )

        val events = repository.getEventsForProgram(programId, since = null)

        assertEquals(listOf(good.id), events.map { it.id })
        assertEquals(1, fakeLogging.warnings.size)
        assertEquals("ProgramChangeEvents", fakeLogging.warnings.single().tag)
        assertEquals(
            "Skipping undecodable program_change_event id=evt-bad type=SCHEME_CHANGED",
            fakeLogging.warnings.single().message,
        )
    }

    @Test
    @DisplayName("decodable rows produce no warning")
    fun decodableRowsProduceNoWarning() = runTest {
        seedProgram()
        repository.append(
            ProgramChangeEvent(
                id = ProgramChangeEventId("evt-good"),
                programId = programId,
                eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                occurredAt = Instant.ofEpochMilli(1000L),
                payload = ProgramChangeEventPayload.ProgramRegenerated,
            ),
        )

        val events = repository.getEventsForProgram(programId, since = null)

        assertEquals(1, events.size)
        assertTrue(fakeLogging.warnings.isEmpty())
    }
}
