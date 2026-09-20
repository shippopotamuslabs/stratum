package com.stratum.core.data.db

import androidx.room.Room
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
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
class NoteDaoTest {

    private lateinit var database: StratumDatabase

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("lift notes read back in position order")
    fun liftNotesOrdered() = runTest {
        database.liftDao().upsert(minimalLift(LiftId("lift-1")))
        database.liftNoteDao().upsertAll(
            listOf(
                liftNote("n-2", "lift-1", "third", position = 2),
                liftNote("n-0", "lift-1", "first", position = 0),
                liftNote("n-1", "lift-1", "second", position = 1),
            ),
        )

        assertEquals(
            listOf("first", "second", "third"),
            database.liftNoteDao().getForLift(LiftId("lift-1")).map { it.body },
        )
    }

    @Test
    @DisplayName("deleting a lift cascades its catalog notes")
    fun liftDeleteCascadesNotes() = runTest {
        database.liftDao().upsert(minimalLift(LiftId("lift-1")))
        database.liftNoteDao().upsertAll(listOf(liftNote("n-1", "lift-1", "pin")))

        database.liftDao().deleteById(LiftId("lift-1"))

        assertTrue(database.liftNoteDao().getForLift(LiftId("lift-1")).isEmpty())
    }

    @Test
    @DisplayName("deleting a program cascades slot notes through the hierarchy")
    fun programDeleteCascadesSlotNotes() = runTest {
        seedSlot(slotId = "wl-1", liftId = "lift-1")
        database.workoutLiftNoteDao().upsertAll(
            listOf(workoutNote("wn-1", "wl-1", "slot pin")),
        )

        database.programDao().deleteById(ProgramId("p-1"))

        assertTrue(database.workoutLiftNoteDao().getForWorkoutLift(WorkoutLiftId("wl-1")).isEmpty())
    }

    @Test
    @DisplayName("pending notes clear only when the timestamp matches")
    fun pendingClearsOnMatch() = runTest {
        database.liftDao().upsert(minimalLift(LiftId("lift-1")))
        database.liftNoteDao().upsertAll(
            listOf(
                liftNote("n-1", "lift-1", "dirty", pending = SyncFlags.PENDING_UPSERT, updatedAt = 7L),
                liftNote("n-2", "lift-1", "other", pending = SyncFlags.PENDING_UPSERT, updatedAt = 8L),
            ),
        )

        database.liftNoteDao().markSyncedIfUnchanged("n-1", 7L)
        database.liftNoteDao().markSyncedIfUnchanged("n-2", 999L)

        assertEquals(
            listOf("other"),
            database.liftNoteDao().getPending().map { it.body },
        )
    }

    @Test
    @DisplayName("a session slot holds several rows in position order")
    fun sessionSlotMultiRow() = runTest {
        insertSession("ts-1")
        val dao = database.liftSessionNoteDao()
        dao.upsert(sessionNote("n-2", "ts-1", "second", position = 1))
        dao.upsert(sessionNote("n-1", "ts-1", "first", position = 0))

        assertEquals(
            listOf("first", "second"),
            dao.getNotesForSlot(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1")).map { it.body },
        )
        assertEquals(
            setOf(LiftSessionNoteId("n-1"), LiftSessionNoteId("n-2")),
            dao.getIdsForSlot(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1")).toSet(),
        )
    }

    private suspend fun seedSlot(slotId: String, liftId: String) {
        database.liftDao().upsert(minimalLift(LiftId(liftId)))
        database.programDao().upsert(
            ProgramEntity(
                id = ProgramId("p-1"),
                name = "P",
                createdAt = "2026-01-01",
                cycleLength = 1,
            ),
        )
        database.workoutDao().upsertAll(
            listOf(
                WorkoutEntity(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Day",
                    weekday = null,
                    position = 0,
                ),
            ),
        )
        database.workoutLiftDao().upsertAll(
            listOf(
                WorkoutLiftEntity(
                    id = WorkoutLiftId(slotId),
                    workoutId = WorkoutId("w-1"),
                    liftId = LiftId(liftId),
                    progressionScheme = ProgressionScheme.Double,
                    position = 0,
                ),
            ),
        )
    }

    private suspend fun insertSession(id: String) {
        database.trainingSessionDao().upsert(
            TrainingSessionEntity(
                id = TrainingSessionId(id),
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

    private fun minimalLift(id: LiftId): LiftEntity =
        LiftEntity(
            id = id,
            name = id.value,
            movementPatternsBitmask = 0,
            primaryMusclesBitmask = 0,
            secondaryMusclesBitmask = 0,
            presetTags = "",
            isCustom = false,
            enabled = true,
        )

    private fun liftNote(
        id: String,
        liftId: String,
        body: String,
        position: Int = 0,
        updatedAt: Long = 1L,
        pending: Int = SyncFlags.CLEAN,
    ): LiftNoteEntity =
        LiftNoteEntity(
            id = LiftCatalogNoteId(id),
            liftId = LiftId(liftId),
            body = body,
            position = position,
            createdAtEpochMillis = updatedAt,
            updatedAtEpochMillis = updatedAt,
            pendingSync = pending,
        )

    private fun workoutNote(id: String, slotId: String, body: String): WorkoutLiftNoteEntity =
        WorkoutLiftNoteEntity(
            id = WorkoutLiftNoteId(id),
            workoutLiftId = WorkoutLiftId(slotId),
            body = body,
        )

    private fun sessionNote(
        id: String,
        sessionId: String,
        body: String,
        position: Int = 0,
    ): LiftSessionNoteEntity =
        LiftSessionNoteEntity(
            id = LiftSessionNoteId(id),
            sessionId = TrainingSessionId(sessionId),
            liftId = LiftId("lift-1"),
            workoutLiftId = WorkoutLiftId("wl-1"),
            body = body,
            position = position,
            updatedAtEpochMillis = 1L,
        )
}
