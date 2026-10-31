package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.db.LiftNoteEntity
import com.stratum.core.infrastructure.db.LiftSessionNoteEntity
import com.stratum.core.infrastructure.db.LiftSetEntity
import com.stratum.core.infrastructure.db.LoggedSetEntity
import com.stratum.core.infrastructure.db.PendingCloudDeleteDao
import com.stratum.core.infrastructure.db.PendingCloudDeleteEntity
import com.stratum.core.infrastructure.db.ProgramEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.db.TrainingSessionEntity
import com.stratum.core.infrastructure.db.WorkoutEntity
import com.stratum.core.infrastructure.db.WorkoutLiftEntity
import com.stratum.core.infrastructure.db.WorkoutLiftNoteEntity
import com.stratum.core.data.sync.CloudDeleteQueue
import com.stratum.core.data.sync.DefaultCloudSyncFeatureGate
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.core.data.sync.UserOwnedWriteCoordinator
import com.stratum.core.data.work.CloudSyncScheduler
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class RoomDeleteLiftPortTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: RoomDeleteLiftPort

    private val liftX = LiftId("lift-x")
    private val liftY = LiftId("lift-y")
    private val liftZ = LiftId("lift-z-custom")

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        subject = RoomDeleteLiftPort(database, database.liftDao(), syncTouch(database.pendingCloudDeleteDao()))
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Delete sweeps slots + stash notes, tombstones the row, keeps history byte-identical")
    fun deleteSweepsAndTombstones() = runTest {
        seedAll()
        val setsBefore = database.trainingLoggedSetDao().getAll()

        val result = subject.deleteLift(liftX)

        assertTrue(result)
        // Row tombstoned: retained, enabled untouched, dirty for is_deleted upsert.
        val row = database.liftDao().getById(liftX)!!
        assertTrue(row.isDeleted)
        assertTrue(row.enabled)
        assertEquals(SyncFlags.PENDING_UPSERT, row.pendingSync)
        assertEquals("pinned cue", row.pinnedNote)

        // Slots for lift-x deleted across all programs (incl. duplicates);
        // the workouts themselves survive, one of them now empty.
        val slots = database.workoutLiftDao().getAll()
        assertEquals(
            setOf(WorkoutLiftId("wl-other"), WorkoutLiftId("wl-z")),
            slots.map { it.id }.toSet(),
        )
        // The workouts themselves survive (w-2 is now empty).
        assertEquals(2, database.workoutDao().getForProgram("p-1").size)
        assertEquals(1, database.workoutDao().getForProgram("p-2").size)
        // Template sets of deleted slots cascade; the surviving slot keeps its.
        assertEquals(
            listOf(LiftSetId("ls-other")),
            database.liftSetDao().getForWorkoutLift("wl-other").map { it.id },
        )
        assertTrue(database.liftSetDao().getForWorkoutLift("wl-dup-1").isEmpty())
        // Stash sweep: the note scoped to lift-x on the surviving slot is gone;
        // the occupying lift's own note survives.
        val notes = database.workoutLiftNoteDao().getForWorkoutLifts(
            listOf(WorkoutLiftId("wl-other")),
        )
        assertEquals(listOf(WorkoutLiftNoteId("wn-y")), notes.map { it.id })

        // History is not a live pointer: logged sets byte-identical, PR intact.
        assertEquals(setsBefore, database.trainingLoggedSetDao().getAll())
        // Catalog notes + session notes are retained with the row.
        assertEquals(1, database.liftNoteDao().getForLift(liftX).size)
        assertEquals(
            1,
            database.liftSessionNoteDao().getForSession(TrainingSessionId("ts-1")).size,
        )

        // Sync: swept rows get delete records; the lift row does NOT (its
        // tombstone propagates as an is_deleted upsert).
        val pending = database.pendingCloudDeleteDao().peekPending(100)
        val byCollection = pending.groupBy { it.collectionPath }
        assertEquals(
            setOf("wl-dup-1", "wl-dup-2", "wl-x-2", "wl-x-3"),
            byCollection[SyncCollection.WorkoutLifts.firestorePath]?.map { it.entityId }?.toSet(),
        )
        assertEquals(
            setOf("ls-dup-1", "ls-x-2"),
            byCollection[SyncCollection.LiftSets.firestorePath]?.map { it.entityId }?.toSet(),
        )
        assertEquals(
            setOf("wn-stash-x", "wn-dup"),
            byCollection[SyncCollection.WorkoutLiftNotes.firestorePath]?.map { it.entityId }?.toSet(),
        )
        assertFalse(pending.any { it.collectionPath == SyncCollection.Lifts.firestorePath })
    }

    @Test
    @DisplayName("Custom lift deletes through the identical path")
    fun deleteCustomLift() = runTest {
        seedAll()
        assertTrue(subject.deleteLift(liftZ))
        val row = database.liftDao().getById(liftZ)!!
        assertTrue(row.isDeleted)
        assertTrue(row.isCustom)
        assertTrue(database.workoutLiftDao().getAll().none { it.liftId == liftZ })
    }

    @Test
    @DisplayName("Second delete is a no-op: false, no sweep, no new sync records")
    fun secondDeleteIsNoOp() = runTest {
        seedAll()
        assertTrue(subject.deleteLift(liftX))
        val pendingAfterFirst = database.pendingCloudDeleteDao().peekPending(100)

        assertFalse(subject.deleteLift(liftX))

        assertEquals(pendingAfterFirst, database.pendingCloudDeleteDao().peekPending(100))
        assertEquals(
            setOf(WorkoutLiftId("wl-other"), WorkoutLiftId("wl-z")),
            database.workoutLiftDao().getAll().map { it.id }.toSet(),
        )
    }

    @Test
    @DisplayName("Missing lift returns false and changes nothing")
    fun missingLift() = runTest {
        seedAll()
        assertFalse(subject.deleteLift(LiftId("nope")))
        assertTrue(database.pendingCloudDeleteDao().peekPending(100).isEmpty())
        assertEquals(6, database.workoutLiftDao().getAll().size)
    }

    @Test
    @DisplayName("A failure inside the transaction rolls back the whole delete")
    fun failureRollsBack() = runTest {
        seedAll()
        val failing =
            RoomDeleteLiftPort(
                database,
                database.liftDao(),
                syncTouch(ThrowingPendingDeleteDao(database.pendingCloudDeleteDao())),
            )

        assertThrows<IllegalStateException> { failing.deleteLift(liftX) }

        // Nothing swept, nothing tombstoned, nothing enqueued.
        assertFalse(database.liftDao().getById(liftX)!!.isDeleted)
        assertEquals(6, database.workoutLiftDao().getAll().size)
        assertEquals(2, database.workoutLiftNoteDao().getForLiftId(liftX).size)
    }

    private suspend fun seedAll() {
        database.liftDao().upsertAll(
            listOf(
                liftRow(liftX, "Lift X", isCustom = false, pinnedNote = "pinned cue"),
                liftRow(liftY, "Lift Y", isCustom = false),
                liftRow(liftZ, "Lift Z", isCustom = true),
            ),
        )
        database.programDao().upsert(
            ProgramEntity(
                id = ProgramId("p-1"),
                name = "Main",
                createdAt = "2026-01-01T00:00:00Z",
                cycleLength = 1,
                isActive = true,
            ),
        )
        database.programDao().upsert(
            ProgramEntity(
                id = ProgramId("p-2"),
                name = "Library",
                createdAt = "2026-01-01T00:00:00Z",
                cycleLength = 1,
                isActive = false,
            ),
        )
        database.workoutDao().upsertAll(
            listOf(
                WorkoutEntity(WorkoutId("w-1"), ProgramId("p-1"), "Push", null, 0),
                WorkoutEntity(WorkoutId("w-2"), ProgramId("p-1"), "Pull", null, 1),
                WorkoutEntity(WorkoutId("w-3"), ProgramId("p-2"), "Legs", null, 0),
            ),
        )
        database.workoutLiftDao().upsertAll(
            listOf(
                slot("wl-dup-1", "w-1", liftX, 0),
                slot("wl-dup-2", "w-1", liftX, 1),
                slot("wl-other", "w-1", liftY, 2),
                slot("wl-x-2", "w-2", liftX, 0),
                slot("wl-x-3", "w-3", liftX, 0),
                slot("wl-z", "w-3", liftZ, 1),
            ),
        )
        database.liftSetDao().upsertAll(
            listOf(
                templateSet("ls-dup-1", "wl-dup-1"),
                templateSet("ls-other", "wl-other"),
                templateSet("ls-x-2", "wl-x-2"),
            ),
        )
        database.workoutLiftNoteDao().upsertAll(
            listOf(
                // Stash: written for lift-x, stashed on the slot lift-y now occupies.
                WorkoutLiftNoteEntity(
                    id = WorkoutLiftNoteId("wn-stash-x"),
                    workoutLiftId = WorkoutLiftId("wl-other"),
                    liftId = liftX,
                    body = "x cue stashed",
                    position = 0,
                ),
                WorkoutLiftNoteEntity(
                    id = WorkoutLiftNoteId("wn-y"),
                    workoutLiftId = WorkoutLiftId("wl-other"),
                    liftId = liftY,
                    body = "y cue",
                    position = 0,
                ),
                WorkoutLiftNoteEntity(
                    id = WorkoutLiftNoteId("wn-dup"),
                    workoutLiftId = WorkoutLiftId("wl-dup-1"),
                    liftId = liftX,
                    body = "x cue on own slot",
                    position = 0,
                ),
            ),
        )
        database.liftNoteDao().upsertAll(
            listOf(
                LiftNoteEntity(
                    id = LiftCatalogNoteId("cn-x"),
                    liftId = liftX,
                    body = "catalog note",
                    position = 0,
                ),
            ),
        )
        database.trainingSessionDao().upsert(
            TrainingSessionEntity(
                id = TrainingSessionId("ts-1"),
                programId = null,
                workoutId = null,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = 2_000L,
                sourceApp = null,
            ),
        )
        database.trainingLoggedSetDao().upsert(
            LoggedSetEntity(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Lift X",
                liftId = liftX,
                setIndex = 0,
                weightAmount = 100.0,
                weightUnit = "Lb",
                reps = 5,
                rir = 1,
                completedAtEpochMillis = 1_500L,
                liftName = "Lift X",
                isPersonalRecord = true,
                estimatedOneRm = 115.0,
            ),
        )
        database.liftSessionNoteDao().upsert(
            LiftSessionNoteEntity(
                id = LiftSessionNoteId("sn-1"),
                sessionId = TrainingSessionId("ts-1"),
                liftId = liftX,
                workoutLiftId = WorkoutLiftId("wl-dup-1"),
                body = "session note",
            ),
        )
    }

    private fun slot(id: String, workoutId: String, liftId: LiftId, position: Int): WorkoutLiftEntity =
        WorkoutLiftEntity(
            id = WorkoutLiftId(id),
            workoutId = WorkoutId(workoutId),
            liftId = liftId,
            progressionScheme = ProgressionScheme.Linear,
            position = position,
        )

    private fun templateSet(id: String, workoutLiftId: String): LiftSetEntity =
        LiftSetEntity(
            id = LiftSetId(id),
            workoutLiftId = WorkoutLiftId(workoutLiftId),
            position = 0,
            setType = SetType.Standard,
            repMin = 8,
            repMax = 12,
            rirGoal = 2,
            isTopSet = false,
        )

    private fun liftRow(
        id: LiftId,
        name: String,
        isCustom: Boolean,
        pinnedNote: String? = null,
    ): LiftEntity =
        LiftEntity(
            id = id,
            name = name,
            movementPatternsBitmask = 0,
            primaryMusclesBitmask = 0,
            secondaryMusclesBitmask = 0,
            presetTags = ",",
            isCustom = isCustom,
            enabled = true,
            pinnedNote = pinnedNote,
            pendingSync = SyncFlags.CLEAN,
        )

    private fun syncTouch(pendingDao: PendingCloudDeleteDao): SyncTouch {
        val gate = DefaultCloudSyncFeatureGate().apply { setEnabledForTests(false) }
        return SyncTouch(
            cloudDeleteQueue = CloudDeleteQueue(pendingDao),
            cloudSyncScheduler = CloudSyncScheduler(RuntimeEnvironment.getApplication()),
            featureGate = gate,
            writeCoordinator = UserOwnedWriteCoordinator(),
        )
    }

    /** Pending-delete DAO whose writes always fail — forces a mid-transaction error. */
    private class ThrowingPendingDeleteDao(
        delegate: PendingCloudDeleteDao,
    ) : PendingCloudDeleteDao by delegate {
        override suspend fun upsertAll(rows: List<PendingCloudDeleteEntity>) {
            throw IllegalStateException("forced enqueue failure")
        }
    }
}
