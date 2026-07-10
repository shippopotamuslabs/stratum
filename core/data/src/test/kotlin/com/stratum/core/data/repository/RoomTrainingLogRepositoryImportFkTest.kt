package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.data.db.ProgramEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.WorkoutEntity
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.WorkoutLoggingFixtures.importLoggedSet
import com.stratum.core.testing.training.WorkoutLoggingFixtures.importTrainingSession
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class RoomTrainingLogRepositoryImportFkTest {
    private lateinit var database: StratumDatabase
    private lateinit var repository: RoomTrainingLogRepository

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomTrainingLogRepository(
            database = database,
            sessionDao = database.trainingSessionDao(),
            setDao = database.trainingLoggedSetDao(),
            programDao = database.programDao(),
            programChangeEventDao = database.programChangeEventDao(),
            syncTouch = com.stratum.core.data.sync.testSyncTouch(RuntimeEnvironment.getApplication(), database.pendingCloudDeleteDao()),
        )
        runBlocking {
            database.programDao().upsert(
                ProgramEntity(
                    id = WorkoutLoggingFixtures.programId,
                    name = "Test Program",
                    createdAt = "2025-01-01T00:00:00Z",
                    cycleLength = 2,
                ),
            )
            database.workoutDao().upsertAll(
                listOf(
                    WorkoutEntity(
                        id = WorkoutLoggingFixtures.workoutAId,
                        programId = WorkoutLoggingFixtures.programId,
                        name = "Workout A",
                        weekday = Weekday.Monday,
                        position = 0,
                    ),
                ),
            )
        }
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("replaceImportHistory persists import sessions in training_sessions before logged_sets FK insert")
    fun replaceImportHistorySatisfiesLoggedSetsForeignKey() {
        val sessionId = "import-session-1"
        val sessions =
            listOf(
                importTrainingSession(
                    id = sessionId,
                    startedAt = java.time.Instant.parse("2025-01-01T10:00:00Z"),
                    endedAt = java.time.Instant.parse("2025-01-01T11:00:00Z"),
                    sourceApp = "test",
                ),
            )
        val sets =
            listOf(
                importLoggedSet(
                    id = "set-1",
                    sessionId = sessionId,
                    exerciseNameRaw = "Bench Press",
                    completedAt = java.time.Instant.parse("2025-01-01T10:30:00Z"),
                ),
            )

        runBlocking { repository.replaceImportHistory(sessions, sets) }

        val storedSession = runBlocking {
            database.trainingSessionDao().getById(TrainingSessionId(sessionId))
        }
        assertNotNull(storedSession)
        assertEquals(TrainingSessionOrigin.IMPORT, storedSession!!.origin)
        assertEquals(1, runBlocking { database.trainingLoggedSetDao().getForSession(storedSession.id).size })
    }

    @Test
    @DisplayName("replaceImportHistory replaces only import rows and preserves live workout sessions")
    fun replaceImportHistoryPreservesLiveSessions() {
        val liveSession = WorkoutLoggingFixtures.completedLiveSession(id = "live-session-1")
        val liveSet =
            WorkoutLoggingFixtures.loggedSet(
                id = "live-set-1",
                sessionId = liveSession.id,
            )
        runBlocking {
            repository.insertSession(liveSession)
            repository.insertSet(liveSet)
        }

        val importSessionId = "import-session-2"
        runBlocking {
            repository.replaceImportHistory(
                sessions =
                    listOf(
                        importTrainingSession(
                            id = importSessionId,
                            startedAt = java.time.Instant.parse("2025-02-01T10:00:00Z"),
                            endedAt = java.time.Instant.parse("2025-02-01T11:00:00Z"),
                        ),
                    ),
                sets =
                    listOf(
                        importLoggedSet(
                            id = "import-set-2",
                            sessionId = importSessionId,
                            completedAt = java.time.Instant.parse("2025-02-01T10:30:00Z"),
                        ),
                    ),
            )
        }

        runBlocking {
            assertNotNull(repository.getSession(liveSession.id))
            assertEquals(1, repository.getSetsForSession(liveSession.id).size)
            assertEquals(TrainingSessionOrigin.LIVE, repository.getSession(liveSession.id)!!.origin)
            assertEquals(1, repository.getImportSessions().size)
            assertEquals(1, repository.getImportSets().size)
            assertNull(repository.getSession(TrainingSessionId("import-session-1")))
        }
    }
}
