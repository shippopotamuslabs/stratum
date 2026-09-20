package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingSessionEntity
import com.stratum.core.data.sync.SyncCollection
import com.stratum.domain.lift.LiftSessionNote
import com.stratum.domain.lift.SessionNoteInput
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
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
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class RoomLiftNotesRepositoryTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: RoomLiftNotesRepository

    private val sessionId = TrainingSessionId("ts-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("squat")

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        subject = RoomLiftNotesRepository(
            database = database,
            dao = database.liftSessionNoteDao(),
            syncTouch = com.stratum.core.data.sync.testSyncTouch(
                RuntimeEnvironment.getApplication(),
                database.pendingCloudDeleteDao(),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("replace stores several notes in display order")
    fun replaceMultiRow() = runTest {
        insertSession()

        subject.replaceSessionNotes(
            sessionId = sessionId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            notes = listOf(
                SessionNoteInput(body = "first"),
                SessionNoteInput(body = "second"),
            ),
        )

        val stored = subject.getSessionNotes(sessionId, workoutLiftId)
        assertEquals(listOf("first", "second"), stored.map { it.body })
        assertEquals(listOf(0, 1), stored.map { it.position })
        assertEquals("first", subject.getSessionNote(sessionId, workoutLiftId)?.body)
    }

    @Test
    @DisplayName("replace keeps known ids and tombstones removed rows")
    fun replaceTombstonesRemoved() = runTest {
        insertSession()
        subject.replaceSessionNotes(
            sessionId = sessionId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            notes = listOf(
                SessionNoteInput(body = "keep"),
                SessionNoteInput(body = "drop"),
            ),
        )
        val keptId = subject.getSessionNotes(sessionId, workoutLiftId).first().id
        val droppedId =
            subject.getSessionNotes(sessionId, workoutLiftId).last().id

        subject.replaceSessionNotes(
            sessionId = sessionId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            notes = listOf(SessionNoteInput(id = keptId, body = "keep edited")),
        )

        val stored = subject.getSessionNotes(sessionId, workoutLiftId)
        assertEquals(listOf("keep edited"), stored.map { it.body })
        assertEquals(keptId, stored.single().id)
        val tombstones = database.pendingCloudDeleteDao().peekPending()
        assertTrue(
            tombstones.any {
                it.collectionPath == SyncCollection.LiftSessionNotes.firestorePath &&
                    it.entityId == droppedId.value
            },
        )
    }

    @Test
    @DisplayName("single-note upsert retains sibling rows in the slot")
    fun surgicalUpsertRetainsSiblings() = runTest {
        insertSession()
        subject.replaceSessionNotes(
            sessionId = sessionId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            notes = listOf(
                SessionNoteInput(body = "first"),
                SessionNoteInput(body = "second"),
            ),
        )
        val before = subject.getSessionNotes(sessionId, workoutLiftId)
        val target = before.first()

        subject.upsertSessionNote(
            target.copy(body = "first edited", updatedAt = Instant.now()),
        )

        val stored = subject.getSessionNotes(sessionId, workoutLiftId)
        assertEquals(listOf("first edited", "second"), stored.map { it.body })
        assertEquals(target.id, stored.first().id)
        assertEquals(target.position, stored.first().position)
    }

    @Test
    @DisplayName("empty replace clears the slot")
    fun emptyReplaceClears() = runTest {
        insertSession()
        subject.upsertSessionNote(
            LiftSessionNote(
                id = LiftSessionNoteId("n-1"),
                sessionId = sessionId,
                liftId = liftId,
                workoutLiftId = workoutLiftId,
                body = "old",
                updatedAt = Instant.EPOCH,
            ),
        )

        subject.replaceSessionNotes(
            sessionId = sessionId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            notes = emptyList(),
        )

        assertTrue(subject.getSessionNotes(sessionId, workoutLiftId).isEmpty())
    }

    private suspend fun insertSession() {
        database.trainingSessionDao().upsert(
            TrainingSessionEntity(
                id = sessionId,
                programId = null,
                workoutId = null,
                status = TrainingSessionStatus.IN_PROGRESS,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAtEpochMillis = 10L,
                endedAtEpochMillis = null,
                sourceApp = null,
                workoutNameSnapshot = null,
            ),
        )
    }
}
