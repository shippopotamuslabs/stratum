package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.db.LiftSessionNoteEntity
import com.stratum.core.infrastructure.db.LiftSetEntity
import com.stratum.core.infrastructure.db.LoggedSetEntity
import com.stratum.core.infrastructure.db.ProgramEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.db.TrainingSessionEntity
import com.stratum.core.infrastructure.db.WorkoutEntity
import com.stratum.core.infrastructure.db.WorkoutLiftEntity
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
class RoomMergeLiftsPortTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: RoomMergeLiftsPort

    private val targetId = LiftId("lift-target")
    private val seedSourceId = LiftId("lift-seed-source")
    private val customSourceId = LiftId("lift-custom-source")

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        val syncTouch =
            com.stratum.core.data.sync.testSyncTouch(
                RuntimeEnvironment.getApplication(),
                database.pendingCloudDeleteDao(),
            )
        subject =
            RoomMergeLiftsPort(
                database = database,
                loggedSetDao = database.trainingLoggedSetDao(),
                workoutLiftDao = database.workoutLiftDao(),
                liftDao = database.liftDao(),
                liftSessionNoteDao = database.liftSessionNoteDao(),
                syncTouch = syncTouch,
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Reassigns history, tombstones seed, deletes custom, repoints templates in one txn")
    fun fullMergeHappyPath() = runTest {
        seedLifts()
        seedSessionAndSets()
        seedProgramWithSourceAndTarget()
        database.liftSessionNoteDao().upsert(
            LiftSessionNoteEntity(
                id = LiftSessionNoteId("n-source"),
                sessionId = TrainingSessionId("ts-1"),
                liftId = seedSourceId,
                workoutLiftId = WorkoutLiftId("wl-source"),
                body = "cue from source",
            ),
        )

        subject.applyMerge(
            targetLiftId = targetId,
            targetName = "Target Lift",
            sourcesToTombstone = setOf(seedSourceId),
            sourcesToDelete = setOf(customSourceId),
        )

        val sets = database.trainingLoggedSetDao().getAll()
        assertEquals(3, sets.size)
        assertTrue(sets.all { it.liftId == targetId })
        assertTrue(sets.all { it.liftName == "Target Lift" })
        assertTrue(sets.all { it.exerciseNameRaw == "Target Lift" })
        assertTrue(sets.none { it.isPersonalRecord == true })

        // STR-131: the seed source is tombstoned, not disabled — enabled is
        // left exactly as it was, and the row is marked for an is_deleted upsert.
        val seed = database.liftDao().getById(seedSourceId)!!
        assertTrue(seed.isDeleted)
        assertTrue(seed.enabled)
        assertEquals(SyncFlags.PENDING_UPSERT, seed.pendingSync)
        assertNull(database.liftDao().getById(customSourceId))

        val slots = database.workoutLiftDao().getAll().sortedBy { it.position }
        // Source + target both remain; source is re-pointed onto the survivor (no collapse).
        assertEquals(2, slots.size)
        assertTrue(slots.all { it.liftId == targetId })
        assertEquals(
            setOf(WorkoutLiftId("wl-source"), WorkoutLiftId("wl-target")),
            slots.map { it.id }.toSet(),
        )

        val pending = database.pendingCloudDeleteDao().peekPending(20)
        assertTrue(
            pending.any {
                it.collectionPath == SyncCollection.Lifts.firestorePath &&
                    it.entityId == customSourceId.value
            },
        )

        val remappedNote =
            database.liftSessionNoteDao().getBySessionAndWorkoutLift(
                TrainingSessionId("ts-1"),
                WorkoutLiftId("wl-source"),
            )
        assertEquals(targetId, remappedNote?.liftId)
        assertEquals("cue from source", remappedNote?.body)
    }

    @Test
    @DisplayName("Given source and target slots in same workout when merged then both slots keep identity on target")
    fun repointsSourceKeepsBothSlots() = runTest {
        seedLifts()
        seedProgramWithSourceAndTarget()

        subject.applyMerge(
            targetLiftId = targetId,
            targetName = "Target Lift",
            sourcesToTombstone = setOf(seedSourceId),
            sourcesToDelete = emptySet(),
        )

        val slots = database.workoutLiftDao().getAll().sortedBy { it.position }
        assertEquals(2, slots.size)
        assertEquals(
            listOf(WorkoutLiftId("wl-source"), WorkoutLiftId("wl-target")),
            slots.map { it.id },
        )
        assertTrue(slots.all { it.liftId == targetId })
        assertTrue(database.liftSetDao().getForWorkoutLift("wl-target").isNotEmpty())
        assertTrue(database.liftSetDao().getForWorkoutLift("wl-source").isNotEmpty())
    }

    @Test
    @DisplayName("Given only source slots when merged then every source slot is re-pointed to target")
    fun repointsAllSourceSlotsWithoutCollapse() = runTest {
        seedLifts()
        seedProgramSkeleton()
        database.workoutLiftDao().upsertAll(
            listOf(
                WorkoutLiftEntity(
                    id = WorkoutLiftId("wl-a"),
                    workoutId = WorkoutId("w-1"),
                    liftId = seedSourceId,
                    progressionScheme = ProgressionScheme.Linear,
                    position = 0,
                ),
                WorkoutLiftEntity(
                    id = WorkoutLiftId("wl-b"),
                    workoutId = WorkoutId("w-1"),
                    liftId = customSourceId,
                    progressionScheme = ProgressionScheme.Linear,
                    position = 1,
                ),
            ),
        )

        subject.applyMerge(
            targetLiftId = targetId,
            targetName = "Target Lift",
            sourcesToTombstone = setOf(seedSourceId),
            sourcesToDelete = setOf(customSourceId),
        )

        val slots = database.workoutLiftDao().getAll().sortedBy { it.position }
        assertEquals(2, slots.size)
        assertEquals(
            listOf(WorkoutLiftId("wl-a"), WorkoutLiftId("wl-b")),
            slots.map { it.id },
        )
        assertTrue(slots.all { it.liftId == targetId })
    }

    @Test
    @DisplayName("Given PR flags on both sides when merged then unique index holds (no dual true)")
    fun demotesPrFlagsBeforeCollapse() = runTest {
        seedLifts()
        seedSessionAndSets()

        subject.applyMerge(
            targetLiftId = targetId,
            targetName = "Target Lift",
            sourcesToTombstone = setOf(seedSourceId),
            sourcesToDelete = setOf(customSourceId),
        )

        val prTrue = database.trainingLoggedSetDao().getAll().count { it.isPersonalRecord == true }
        assertEquals(0, prTrue)
    }

    @Test
    @DisplayName("STR-131: merging a seed source sweeps its pair-note stashes; target notes survive")
    fun mergeTombstoneSweepsStashNotes() = runTest {
        seedLifts()
        seedProgramWithSourceAndTarget()
        val noteDao = database.workoutLiftNoteDao()
        noteDao.upsertAll(
            listOf(
                com.stratum.core.infrastructure.db.WorkoutLiftNoteEntity(
                    id = com.stratum.domain.program.identity.WorkoutLiftNoteId("wn-stash"),
                    workoutLiftId = WorkoutLiftId("wl-target"),
                    liftId = seedSourceId,
                    body = "source cue stashed on target slot",
                    position = 0,
                ),
                com.stratum.core.infrastructure.db.WorkoutLiftNoteEntity(
                    id = com.stratum.domain.program.identity.WorkoutLiftNoteId("wn-target"),
                    workoutLiftId = WorkoutLiftId("wl-target"),
                    liftId = targetId,
                    body = "target cue",
                    position = 0,
                ),
            ),
        )

        subject.applyMerge(
            targetLiftId = targetId,
            targetName = "Target Lift",
            sourcesToTombstone = setOf(seedSourceId),
            sourcesToDelete = emptySet(),
        )

        assertTrue(noteDao.getForLiftId(seedSourceId).isEmpty())
        assertEquals(
            listOf("target cue"),
            noteDao.getForWorkoutLift(WorkoutLiftId("wl-target")).map { it.body },
        )
        val pending = database.pendingCloudDeleteDao().peekPending(20)
        assertTrue(
            pending.any {
                it.collectionPath == SyncCollection.WorkoutLiftNotes.firestorePath &&
                    it.entityId == "wn-stash"
            },
        )
    }

    private suspend fun seedLifts() {
        database.liftDao().upsertAll(
            listOf(
                liftRow(targetId, "Target Lift", isCustom = false),
                liftRow(seedSourceId, "Seed Source", isCustom = false),
                liftRow(customSourceId, "Custom Source", isCustom = true),
            ),
        )
    }

    private suspend fun seedSessionAndSets() {
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
        val setDao = database.trainingLoggedSetDao()
        setDao.upsert(
            loggedSet("set-target", targetId, "Target Lift", isPr = true),
        )
        setDao.upsert(
            loggedSet("set-seed", seedSourceId, "Seed Source", isPr = true),
        )
        setDao.upsert(
            loggedSet("set-custom", customSourceId, "Custom Source", isPr = true),
        )
    }

    private suspend fun seedProgramSkeleton() {
        database.programDao().upsert(
            ProgramEntity(
                id = ProgramId("p-1"),
                name = "Main",
                createdAt = "2026-01-01T00:00:00Z",
                cycleLength = 1,
                isActive = true,
            ),
        )
        database.workoutDao().upsertAll(
            listOf(
                WorkoutEntity(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push",
                    weekday = null,
                    position = 0,
                ),
            ),
        )
    }

    private suspend fun seedProgramWithSourceAndTarget() {
        seedProgramSkeleton()
        database.workoutLiftDao().upsertAll(
            listOf(
                WorkoutLiftEntity(
                    id = WorkoutLiftId("wl-source"),
                    workoutId = WorkoutId("w-1"),
                    liftId = seedSourceId,
                    progressionScheme = ProgressionScheme.Linear,
                    position = 0,
                ),
                WorkoutLiftEntity(
                    id = WorkoutLiftId("wl-target"),
                    workoutId = WorkoutId("w-1"),
                    liftId = targetId,
                    progressionScheme = ProgressionScheme.Linear,
                    position = 1,
                ),
            ),
        )
        database.liftSetDao().upsertAll(
            listOf(
                LiftSetEntity(
                    id = LiftSetId("ls-source"),
                    workoutLiftId = WorkoutLiftId("wl-source"),
                    position = 0,
                    setType = SetType.Standard,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 2,
                    isTopSet = false,
                ),
                LiftSetEntity(
                    id = LiftSetId("ls-target"),
                    workoutLiftId = WorkoutLiftId("wl-target"),
                    position = 0,
                    setType = SetType.Standard,
                    repMin = 5,
                    repMax = 8,
                    rirGoal = 1,
                    isTopSet = true,
                ),
            ),
        )
    }

    private fun liftRow(id: LiftId, name: String, isCustom: Boolean): LiftEntity =
        LiftEntity(
            id = id,
            name = name,
            movementPatternsBitmask = 0,
            primaryMusclesBitmask = 0,
            secondaryMusclesBitmask = 0,
            presetTags = ",",
            isCustom = isCustom,
            enabled = true,
            pendingSync = SyncFlags.CLEAN,
        )

    private fun loggedSet(
        id: String,
        liftId: LiftId,
        name: String,
        isPr: Boolean,
    ): LoggedSetEntity =
        LoggedSetEntity(
            id = LoggedSetId(id),
            sessionId = TrainingSessionId("ts-1"),
            exerciseNameRaw = name,
            liftId = liftId,
            setIndex = 0,
            weightAmount = 100.0,
            weightUnit = "Lb",
            reps = 5,
            rir = 1,
            completedAtEpochMillis = 1_500L,
            liftName = name,
            isPersonalRecord = if (isPr) true else null,
            estimatedOneRm = 115.0,
        )
}
