package com.stratum.core.infrastructure.db

import androidx.room.Room
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LiftSessionNoteDaoTest {

    private lateinit var database: StratumDatabase
    private lateinit var notes: LiftSessionNoteDao

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        notes = database.liftSessionNoteDao()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("unique session+workoutLift upserts reuse the same row")
    fun uniqueUpsert() = runTest {
        insertSession("ts-1", TrainingSessionStatus.IN_PROGRESS, startedAt = 10L)
        val first =
            LiftSessionNoteEntity(
                id = LiftSessionNoteId("n-1"),
                sessionId = TrainingSessionId("ts-1"),
                liftId = LiftId("squat"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                body = "first",
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.PENDING_UPSERT,
            )
        notes.upsert(first)
        notes.upsert(first.copy(body = "second", updatedAtEpochMillis = 2L))

        val loaded = notes.getBySessionAndWorkoutLift(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1"))
        assertEquals("n-1", loaded?.id?.value)
        assertEquals("second", loaded?.body)
    }

    @Test
    @DisplayName("same slot holds several rows with distinct primary keys")
    fun sameSlotMultiRow() = runTest {
        insertSession("ts-1", TrainingSessionStatus.IN_PROGRESS, startedAt = 10L)
        notes.upsert(
            LiftSessionNoteEntity(
                id = LiftSessionNoteId("n-a"),
                sessionId = TrainingSessionId("ts-1"),
                liftId = LiftId("squat"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                body = "first",
                position = 0,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.PENDING_UPSERT,
            ),
        )
        notes.upsert(
            LiftSessionNoteEntity(
                id = LiftSessionNoteId("n-b"),
                sessionId = TrainingSessionId("ts-1"),
                liftId = LiftId("squat"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                body = "second",
                position = 1,
                updatedAtEpochMillis = 2L,
                pendingSync = SyncFlags.PENDING_UPSERT,
            ),
        )
        val loaded = notes.getNotesForSlot(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1"))
        assertEquals(listOf("first", "second"), loaded.map { it.body })
        assertEquals("n-a", notes.getById(LiftSessionNoteId("n-a"))?.id?.value)
    }

    @Test
    @DisplayName("past notes skip in-progress, blanks, and excluded session")
    fun pastNotesFilter() = runTest {
        insertSession("ts-old", TrainingSessionStatus.COMPLETED, startedAt = 1L, name = "Lower A")
        insertSession("ts-new", TrainingSessionStatus.COMPLETED, startedAt = 9L, name = "Lower B")
        insertSession("ts-live", TrainingSessionStatus.IN_PROGRESS, startedAt = 10L)
        insertSession("ts-current", TrainingSessionStatus.COMPLETED, startedAt = 11L, name = "Today")

        notes.upsert(note("n-old", "ts-old", "squat", "old body"))
        notes.upsert(note("n-new", "ts-new", "squat", "new body"))
        notes.upsert(note("n-blank", "ts-new", "squat", "", workoutLiftId = "wl-blank"))
        notes.upsert(note("n-other", "ts-new", "bench", "other", workoutLiftId = "wl-other"))
        notes.upsert(note("n-live", "ts-live", "squat", "live"))
        notes.upsert(note("n-cur", "ts-current", "squat", "current"))

        val past =
            notes.getPastNotesForLiftExcluding(LiftId("squat"), TrainingSessionId("ts-current"))
        assertEquals(listOf("new body", "old body"), past.map { it.note.body })
        assertEquals("Lower B", past.first().workoutNameSnapshot)
        assertNull(
            notes.getPastNotesForLiftExcluding(LiftId("squat"), TrainingSessionId("ts-current"))
                .firstOrNull { it.note.body.isBlank() },
        )
    }

    @Test
    @DisplayName("past notes order within a session follows position, not insert order")
    fun pastNotesPositionOrder() = runTest {
        insertSession("ts-old", TrainingSessionStatus.COMPLETED, startedAt = 1L, name = "Lower A")
        notes.upsert(note("n-second", "ts-old", "squat", "second").copy(position = 1))
        notes.upsert(note("n-first", "ts-old", "squat", "first").copy(position = 0))

        val past = notes.getPastNotesForLift(LiftId("squat"))

        assertEquals(listOf("first", "second"), past.map { it.note.body })
    }

    @Test
    @DisplayName("excluding variant orders within a session by position too")
    fun pastNotesExcludingPositionOrder() = runTest {
        insertSession("ts-old", TrainingSessionStatus.COMPLETED, startedAt = 1L, name = "Lower A")
        insertSession("ts-cur", TrainingSessionStatus.COMPLETED, startedAt = 9L, name = "Today")
        notes.upsert(note("n-second", "ts-old", "squat", "second").copy(position = 1))
        notes.upsert(note("n-first", "ts-old", "squat", "first").copy(position = 0))
        notes.upsert(note("n-cur", "ts-cur", "squat", "current"))

        val past =
            notes.getPastNotesForLiftExcluding(LiftId("squat"), TrainingSessionId("ts-cur"))

        assertEquals(listOf("first", "second"), past.map { it.note.body })
    }

    private suspend fun insertSession(
        id: String,
        status: TrainingSessionStatus,
        startedAt: Long,
        name: String? = null,
    ) {
        database.trainingSessionDao().upsert(
            TrainingSessionEntity(
                id = TrainingSessionId(id),
                programId = null,
                workoutId = null,
                status = status,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAtEpochMillis = startedAt,
                endedAtEpochMillis = if (status == TrainingSessionStatus.COMPLETED) startedAt + 1 else null,
                sourceApp = null,
                workoutNameSnapshot = name,
            ),
        )
    }

    private fun note(
        id: String,
        sessionId: String,
        liftId: String,
        body: String,
        workoutLiftId: String = "wl-$id",
    ): LiftSessionNoteEntity =
        LiftSessionNoteEntity(
            id = LiftSessionNoteId(id),
            sessionId = TrainingSessionId(sessionId),
            liftId = LiftId(liftId),
            workoutLiftId = WorkoutLiftId(workoutLiftId),
            body = body,
            updatedAtEpochMillis = 1L,
        )
}
