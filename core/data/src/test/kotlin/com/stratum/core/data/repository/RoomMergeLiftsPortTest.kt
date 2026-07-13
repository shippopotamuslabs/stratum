package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.LiftSetEntity
import com.stratum.core.data.db.LoggedSetEntity
import com.stratum.core.data.db.ProgramEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingSessionEntity
import com.stratum.core.data.db.WorkoutEntity
import com.stratum.core.data.db.WorkoutLiftEntity
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
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
import org.junit.jupiter.api.Assertions.assertFalse
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
                liftSetDao = database.liftSetDao(),
                liftDao = database.liftDao(),
                syncTouch = syncTouch,
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Reassigns history, hides seed, deletes custom, repoints templates in one txn")
    fun fullMergeHappyPath() = runTest {
        seedLifts()
        seedSessionAndSets()
        seedProgramWithSourceAndTarget()

        subject.applyMerge(
            targetLiftId = targetId,
            targetName = "Target Lift",
            sourcesToHide = setOf(seedSourceId),
            sourcesToDelete = setOf(customSourceId),
        )

        val sets = database.trainingLoggedSetDao().getAll()
        assertEquals(3, sets.size)
        assertTrue(sets.all { it.liftId == targetId })
        assertTrue(sets.all { it.liftName == "Target Lift" })
        assertTrue(sets.all { it.exerciseNameRaw == "Target Lift" })
        assertTrue(sets.none { it.isPersonalRecord == true })

        val seed = database.liftDao().getById(seedSourceId)!!
        assertFalse(seed.enabled)
        assertNull(database.liftDao().getById(customSourceId))

        val slots = database.workoutLiftDao().getAll()
        assertEquals(1, slots.size)
        assertEquals(targetId, slots.single().liftId)
        assertEquals(WorkoutLiftId("wl-target"), slots.single().id)

        val pending = database.pendingCloudDeleteDao().peekPending(20)
        assertTrue(
            pending.any {
                it.collectionPath == SyncCollection.Lifts.firestorePath &&
                    it.entityId == customSourceId.value
            },
        )
    }

    @Test
    @DisplayName("Given source and target slots in same workout when merged then keeps target slot only")
    fun collapsesDuplicateSlotsPreferringTarget() = runTest {
        seedLifts()
        seedProgramWithSourceAndTarget()

        subject.applyMerge(
            targetLiftId = targetId,
            targetName = "Target Lift",
            sourcesToHide = setOf(seedSourceId),
            sourcesToDelete = emptySet(),
        )

        val slots = database.workoutLiftDao().getAll()
        assertEquals(1, slots.size)
        assertEquals(WorkoutLiftId("wl-target"), slots.single().id)
        assertTrue(database.liftSetDao().getForWorkoutLift("wl-target").isNotEmpty())
        assertTrue(database.liftSetDao().getForWorkoutLift("wl-source").isEmpty())
    }

    @Test
    @DisplayName("Given only source slots when merged then repoints lowest-position keeper")
    fun repointsWhenNoExistingTargetSlot() = runTest {
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
            sourcesToHide = setOf(seedSourceId),
            sourcesToDelete = setOf(customSourceId),
        )

        val slots = database.workoutLiftDao().getAll()
        assertEquals(1, slots.size)
        assertEquals(WorkoutLiftId("wl-a"), slots.single().id)
        assertEquals(targetId, slots.single().liftId)
    }

    @Test
    @DisplayName("Given PR flags on both sides when merged then unique index holds (no dual true)")
    fun demotesPrFlagsBeforeCollapse() = runTest {
        seedLifts()
        seedSessionAndSets()

        subject.applyMerge(
            targetLiftId = targetId,
            targetName = "Target Lift",
            sourcesToHide = setOf(seedSourceId),
            sourcesToDelete = setOf(customSourceId),
        )

        val prTrue = database.trainingLoggedSetDao().getAll().count { it.isPersonalRecord == true }
        assertEquals(0, prTrue)
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
