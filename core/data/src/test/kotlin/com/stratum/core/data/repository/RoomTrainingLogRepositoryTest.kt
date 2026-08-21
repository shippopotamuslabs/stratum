package com.stratum.core.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.stratum.core.data.db.StratumDatabase
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class RoomTrainingLogRepositoryTest {
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
            liftSessionNoteDao = database.liftSessionNoteDao(),
            syncTouch = com.stratum.core.data.sync.testSyncTouch(RuntimeEnvironment.getApplication(), database.pendingCloudDeleteDao()),
        )
        runBlocking {
            database.programDao().upsert(
                com.stratum.core.data.db.ProgramEntity(
                    id = WorkoutLoggingFixtures.programId,
                    name = "Test Program",
                    createdAt = "2025-01-01T00:00:00Z",
                    cycleLength = 2,
                ),
            )
            database.workoutDao().upsertAll(
                listOf(
                    com.stratum.core.data.db.WorkoutEntity(
                        id = WorkoutLoggingFixtures.workoutAId,
                        programId = WorkoutLoggingFixtures.programId,
                        name = "Workout A",
                        weekday = com.stratum.domain.onboarding.Weekday.Monday,
                        position = 0,
                    ),
                    com.stratum.core.data.db.WorkoutEntity(
                        id = WorkoutLoggingFixtures.workoutBId,
                        programId = WorkoutLoggingFixtures.programId,
                        name = "Workout B",
                        weekday = com.stratum.domain.onboarding.Weekday.Wednesday,
                        position = 1,
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
    @DisplayName("WL-008 insertSession persists an IN_PROGRESS training session")
    fun wl008_insertSessionPersists() = runTest {
        val session = WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1")
        repository.insertSession(session)

        assertEquals(TrainingSessionStatus.IN_PROGRESS, repository.getSession(TrainingSessionId("ts-live-1"))?.status)
    }

    @Test
    @DisplayName("WL-009 updateSession changes session status to COMPLETED")
    fun wl009_updateSessionChangesStatus() = runTest {
        val session = WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1")
        repository.insertSession(session)
        val completed = session.copy(
            status = TrainingSessionStatus.COMPLETED,
            endedAt = java.time.Instant.parse("2025-01-01T12:00:00Z"),
        )
        repository.updateSession(completed)

        val read = repository.getSession(TrainingSessionId("ts-live-1"))
        assertEquals(TrainingSessionStatus.COMPLETED, read?.status)
        assertNotNull(read?.endedAt)
    }

    @Test
    @DisplayName("WL-010 deleteSession removes session and cascaded sets")
    fun wl010_deleteSessionCascadesSets() = runTest {
        val session = WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1")
        repository.insertSession(session)
        repository.insertSet(
            WorkoutLoggingFixtures.loggedSet("set-1", TrainingSessionId("ts-live-1")),
        )

        repository.deleteSession(TrainingSessionId("ts-live-1"))

        assertNull(repository.getSession(TrainingSessionId("ts-live-1")))
        assertTrue(repository.getSetsForSession(TrainingSessionId("ts-live-1")).isEmpty())
    }

    @Test
    @DisplayName("deleteSession tombstones lift_session_notes before CASCADE")
    fun deleteSessionTombstonesLiftSessionNotes() = runTest {
        val session = WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1")
        repository.insertSession(session)
        database.liftSessionNoteDao().upsert(
            com.stratum.core.data.db.LiftSessionNoteEntity(
                id = com.stratum.domain.lift.identity.LiftSessionNoteId("n-1"),
                sessionId = TrainingSessionId("ts-live-1"),
                liftId = com.stratum.domain.lift.identity.LiftId("squat"),
                workoutLiftId = com.stratum.domain.program.identity.WorkoutLiftId("wl-1"),
                body = "session cue",
            ),
        )

        repository.deleteSession(TrainingSessionId("ts-live-1"))

        assertNull(
            database.liftSessionNoteDao().getById(
                com.stratum.domain.lift.identity.LiftSessionNoteId("n-1"),
            ),
        )
        val pending = database.pendingCloudDeleteDao().peekPending(20)
        assertTrue(
            pending.any {
                it.collectionPath ==
                    com.stratum.core.data.sync.SyncCollection.LiftSessionNotes.firestorePath &&
                    it.entityId == "n-1"
            },
        )
    }

    @Test
    @DisplayName("WL-011 observeActiveSession emits the IN_PROGRESS session")
    fun wl011_observeActiveSessionEmits() = runTest {
        repository.insertSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-active"))

        repository.observeActiveSession().test {
            assertEquals("ts-active", awaitItem()?.id?.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("WL-012 observeActiveSession emits null when no IN_PROGRESS session exists")
    fun wl012_observeActiveSessionEmitsNull() = runTest {
        repository.insertSession(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-done", WorkoutLoggingFixtures.workoutAId),
        )

        repository.observeActiveSession().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("WL-013 getCompletedSessionsForCycle returns sessions for program and cycle only")
    fun wl013_getCompletedSessionsForCycleFilters() = runTest {
        repository.insertSession(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-a", WorkoutLoggingFixtures.workoutAId, 1),
        )
        repository.insertSession(
            WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-b", WorkoutLoggingFixtures.workoutBId, 2),
        )

        val result = repository.getCompletedSessionsForCycle(WorkoutLoggingFixtures.programId, 1)

        assertEquals(1, result.size)
        assertEquals(WorkoutLoggingFixtures.workoutAId, result.single().workoutId)
    }
}
