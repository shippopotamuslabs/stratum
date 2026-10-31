package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.infrastructure.db.ProgramChangeEventEntity
import com.stratum.core.infrastructure.db.ProgramEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.data.sync.testSyncTouch
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.SkippedProgramChangeEventRow
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
 * [RoomProgramChangeEventRepository] undecodable-row tests (STR-260). Runs with
 * Robolectric so a real in-memory [StratumDatabase] backs the DAO. The repository
 * returns skipped rows as diagnostic values on the read (CODE_STYLE_RULES:
 * repository implementations never log); draining them is the caller's job.
 */
@ExtendWith(RobolectricExtension::class)
class RoomProgramChangeEventRepositoryTest {
    private lateinit var database: StratumDatabase
    private lateinit var repository: RoomProgramChangeEventRepository

    private val programId = ProgramId("prog-1")

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomProgramChangeEventRepository(
            dao = database.programChangeEventDao(),
            syncTouch = testSyncTouch(RuntimeEnvironment.getApplication(), database.pendingCloudDeleteDao()),
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
    @DisplayName("undecodable row is skipped and returned as a diagnostic value")
    fun undecodableRowSkippedAndReturnedAsDiagnostic() = runTest {
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

        val read = repository.getEventsForProgram(programId, since = null)

        assertEquals(listOf(good.id), read.events.map { it.id })
        assertEquals(
            listOf(
                SkippedProgramChangeEventRow(
                    id = ProgramChangeEventId("evt-bad"),
                    eventType = ProgramChangeEventType.SCHEME_CHANGED,
                ),
            ),
            read.skippedRows,
        )
    }

    @Test
    @DisplayName("decodable rows produce no diagnostics")
    fun decodableRowsProduceNoDiagnostics() = runTest {
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

        val read = repository.getEventsForProgram(programId, since = null)

        assertEquals(1, read.events.size)
        assertTrue(read.skippedRows.isEmpty())
    }
}
