package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.infrastructure.db.EquipmentEntity
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.mapper.toBitmask
import com.stratum.core.infrastructure.mapper.toPresetCsv
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.core.data.sync.testSyncTouch
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
@DisplayName("RoomEquipmentRepository tombstone deletion")
class RoomEquipmentRepositoryTombstoneTest {

    private val lb = Weight.WeightUnit.Lb
    private lateinit var database: StratumDatabase
    private lateinit var equipmentRepository: RoomEquipmentRepository
    private lateinit var liftRepository: RoomLiftRepository

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        val syncTouch =
            testSyncTouch(
                RuntimeEnvironment.getApplication(),
                database.pendingCloudDeleteDao(),
            )
        equipmentRepository =
            RoomEquipmentRepository(
                database = database,
                equipmentDao = database.equipmentDao(),
                incrementTierDao = database.incrementTierDao(),
                liftDao = database.liftDao(),
                syncTouch = syncTouch,
            )
        liftRepository =
            RoomLiftRepository(
                database = database,
                liftDao = database.liftDao(),
                incrementTierDao = database.incrementTierDao(),
                equipmentDao = database.equipmentDao(),
                equipmentRepository = equipmentRepository,
                gymPreferenceDao = database.gymPreferenceDao(),
                syncTouch = syncTouch,
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("delete tombstones the row: flag set, name and tiers retained, marked dirty, no cloud delete")
    fun deleteTombstonesRow() = runTest {
        val id =
            equipmentRepository.addCustomEquipment(
                name = "Old Stack",
                tiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb))),
            )

        assertTrue(equipmentRepository.deleteCustomEquipment(id))

        val row = database.equipmentDao().getById(id)
        assertNotNull(row)
        assertEquals("Old Stack", row!!.name)
        assertTrue(row.isDeleted)
        assertEquals(SyncFlags.PENDING_UPSERT, row.pendingSync)
        val resolved = equipmentRepository.getEquipment(id)
        assertNotNull(resolved)
        assertTrue(resolved!!.isDeleted)
        assertEquals(1, resolved.incrementTiers.size)
        assertTrue(database.pendingCloudDeleteDao().peekPending().isEmpty())
    }

    @Test
    @DisplayName("tombstoned rows leave the catalog but stay resolvable by id")
    fun catalogExcludesTombstoneWhileGetByIdIncludes() = runTest {
        val deletedId = equipmentRepository.addCustomEquipment(name = "Old Stack")
        val liveId = equipmentRepository.addCustomEquipment(name = "New Stack")

        assertTrue(equipmentRepository.deleteCustomEquipment(deletedId))

        val catalog = equipmentRepository.observeEquipment().first()
        assertEquals(listOf(liveId), catalog.map { it.id })
        assertNotNull(equipmentRepository.getEquipment(deletedId))
    }

    @Test
    @DisplayName("deleting assigned equipment re-points the lift (no orphaned pointer)")
    fun liftJoinStillResolvesTombstonedName() = runTest {
        seedBodyweight()
        val id = equipmentRepository.addCustomEquipment(name = "Old Stack")
        val replacement = equipmentRepository.addCustomEquipment(name = "New Stack")
        val liftId = LiftId("lat-pulldown")
        seedLift(liftId, "Lat pulldown", id)

        assertTrue(equipmentRepository.deleteCustomEquipment(id, replacement))

        val lifts = liftRepository.observeLifts().first()
        val lift = lifts.first { it.id == liftId }
        assertEquals(replacement, lift.equipmentId)
        assertEquals("New Stack", lift.equipmentName)
        // History resolution by id still returns the tombstoned row.
        assertEquals("Old Stack", equipmentRepository.getEquipment(id)!!.name)
    }

    @Test
    @DisplayName("findOrCreateByName skips tombstones and mints a new row")
    fun findOrCreateSkipsTombstones() = runTest {
        val first =
            equipmentRepository.addCustomEquipment(
                name = "Old Stack",
                tiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb))),
            )
        assertTrue(equipmentRepository.deleteCustomEquipment(first))

        val second = equipmentRepository.findOrCreateByName("Old Stack")

        assertNotEquals(first, second)
        assertFalse(equipmentRepository.getEquipment(second)!!.isDeleted)
        // The live row now matches by name.
        assertEquals(second, equipmentRepository.findOrCreateByName("old stack"))
    }

    @Test
    @DisplayName("rename is a no-op on tombstoned rows")
    fun renameNoopOnTombstone() = runTest {
        val id = equipmentRepository.addCustomEquipment(name = "Old Stack")
        assertTrue(equipmentRepository.deleteCustomEquipment(id))
        val before = database.equipmentDao().getById(id)!!

        equipmentRepository.renameEquipment(id, "Renamed")

        val after = database.equipmentDao().getById(id)!!
        assertEquals(before.name, after.name)
        assertEquals(before.updatedAtEpochMillis, after.updatedAtEpochMillis)
    }

    @Test
    @DisplayName("setIncrementTiers is a no-op on tombstoned rows")
    fun setTiersNoopOnTombstone() = runTest {
        val id =
            equipmentRepository.addCustomEquipment(
                name = "Old Stack",
                tiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb))),
            )
        assertTrue(equipmentRepository.deleteCustomEquipment(id))

        equipmentRepository.setIncrementTiers(
            id = id,
            tiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb))),
            micro = listOf(Weight(1.0, lb)),
        )

        val resolved = equipmentRepository.getEquipment(id)!!
        assertEquals(2.5, resolved.incrementTiers.single().increment.amount)
        assertTrue(resolved.microIncrements.isEmpty())
    }

    @Test
    @DisplayName("visible seed equipment tombstones; hidden system rows refuse")
    fun seedDeleteRefused() = runTest {
        val seedId = EquipmentId("cable")
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = seedId,
                name = "Cables",
                isCustom = false,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = com.stratum.domain.lift.SeedEquipmentIds.Bodyweight,
                name = "Bodyweight",
                isCustom = false,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )

        assertTrue(equipmentRepository.deleteCustomEquipment(seedId))
        val row = database.equipmentDao().getById(seedId)!!
        assertTrue(row.isDeleted)
        assertEquals(SyncFlags.PENDING_UPSERT, row.pendingSync)
        assertTrue(equipmentRepository.observeEquipment().first().none { it.id == seedId })
        assertNotNull(equipmentRepository.getEquipment(seedId))
        // Hidden rows stay undeletable.
        assertFalse(
            equipmentRepository.deleteCustomEquipment(com.stratum.domain.lift.SeedEquipmentIds.Bodyweight),
        )
        assertFalse(
            equipmentRepository.deleteCustomEquipment(com.stratum.domain.lift.SeedEquipmentIds.Default),
        )
    }

    @Test
    @DisplayName("missing equipment cannot be deleted")
    fun missingDeleteRefused() = runTest {
        assertFalse(equipmentRepository.deleteCustomEquipment(EquipmentId("nope")))
    }

    @Test
    @DisplayName("re-deleting tombstoned equipment succeeds idempotently")
    fun redeleteIsIdempotent() = runTest {
        val id = equipmentRepository.addCustomEquipment(name = "Old Stack")

        assertTrue(equipmentRepository.deleteCustomEquipment(id))
        assertTrue(equipmentRepository.deleteCustomEquipment(id))
        assertTrue(database.equipmentDao().getById(id)!!.isDeleted)
    }

    @Test
    @DisplayName("sweep repoints lifts and slots across programs; null slots untouched; zero live pointers remain")
    fun sweepRepointsAllLivePointers() = runTest {
        seedBodyweight()
        val deleted = equipmentRepository.addCustomEquipment(name = "Old Stack")
        val replacement = equipmentRepository.addCustomEquipment(name = "New Stack")
        seedLift(LiftId("lift-a"), "Lift A", deleted)
        seedLift(LiftId("lift-b"), "Lift B", deleted)
        seedProgramWithSlots(deleted)

        assertTrue(equipmentRepository.deleteCustomEquipment(deleted, replacement))

        val lifts = database.liftDao().getLifts()
        assertTrue(lifts.filter { it.id.value in setOf("lift-a", "lift-b") }.all { it.equipmentId == replacement })
        val slots = database.workoutLiftDao().getAll()
        assertEquals(0, slots.count { it.equipmentId == deleted })
        assertEquals(2, slots.count { it.equipmentId == replacement })
        assertEquals(1, slots.count { it.equipmentId == null })
        assertTrue(slots.filter { it.equipmentId == replacement }.all { it.pendingSync == SyncFlags.PENDING_UPSERT })
        assertTrue(lifts.filter { it.equipmentId == replacement }.all { it.pendingSync == SyncFlags.PENDING_UPSERT })
        assertEquals(SyncFlags.PENDING_UPSERT, database.equipmentDao().getById(deleted)!!.pendingSync)
    }

    @Test
    @DisplayName("missing replacement with candidates fails and changes nothing")
    fun missingReplacementFailsAtomically() = runTest {
        seedBodyweight()
        val deleted = equipmentRepository.addCustomEquipment(name = "Old Stack")
        equipmentRepository.addCustomEquipment(name = "New Stack")
        seedLift(LiftId("lift-a"), "Lift A", deleted)
        seedLift(LiftId("lift-b"), "Lift B", deleted)
        seedProgramWithSlots(deleted)

        assertFalse(equipmentRepository.deleteCustomEquipment(deleted))

        assertFalse(database.equipmentDao().getById(deleted)!!.isDeleted)
        assertEquals(deleted, database.liftDao().getById(LiftId("lift-a"))!!.equipmentId)
        assertEquals(2, database.workoutLiftDao().getAll().count { it.equipmentId == deleted })
    }

    @Test
    @DisplayName("no candidate falls back to Bodyweight with aligned load type")
    fun fallbackToBodyweightRow() = runTest {
        seedBodyweight()
        val deleted = equipmentRepository.addCustomEquipment(name = "Old Stack")
        seedLift(LiftId("lift-a"), "Lift A", deleted)
        seedLift(LiftId("lift-b"), "Lift B", deleted)
        seedProgramWithSlots(deleted)

        assertTrue(equipmentRepository.deleteCustomEquipment(deleted))

        val lift = database.liftDao().getById(LiftId("lift-a"))!!
        assertEquals(com.stratum.domain.lift.SeedEquipmentIds.Bodyweight, lift.equipmentId)
        assertEquals(LoadType.Bodyweight, lift.loadType)
        assertTrue(database.workoutLiftDao().getAll().filter { it.equipmentId != null }
            .all { it.equipmentId == com.stratum.domain.lift.SeedEquipmentIds.Bodyweight })
    }

    @Test
    @DisplayName("logged sets and PR flags are byte-identical after delete-with-reassignment")
    fun historyUntouchedBySweep() = runTest {
        seedBodyweight()
        val deleted = equipmentRepository.addCustomEquipment(name = "Old Stack")
        val replacement = equipmentRepository.addCustomEquipment(name = "New Stack")
        seedLift(LiftId("lift-a"), "Lift A", deleted)
        database.trainingSessionDao().upsert(
            com.stratum.core.infrastructure.db.TrainingSessionEntity(
                id = com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
                programId = null,
                workoutId = null,
                status = com.stratum.domain.training.TrainingSessionStatus.COMPLETED,
                kind = com.stratum.domain.training.TrainingSessionKind.PROGRAM_WORKOUT,
                origin = com.stratum.domain.training.TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = 2_000L,
                sourceApp = null,
            ),
        )
        database.trainingLoggedSetDao().upsert(
            com.stratum.core.infrastructure.db.LoggedSetEntity(
                id = com.stratum.domain.importing.identity.LoggedSetId("set-1"),
                sessionId = com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
                exerciseNameRaw = "Lift A",
                liftId = LiftId("lift-a"),
                equipmentId = deleted,
                setIndex = 0,
                weightAmount = 100.0,
                weightUnit = "Lb",
                reps = 5,
                rir = 1,
                completedAtEpochMillis = 1_500L,
                liftName = "Lift A",
                isPersonalRecord = true,
                estimatedOneRm = 115.0,
            ),
        )
        val before = database.trainingLoggedSetDao().getAll()

        assertTrue(equipmentRepository.deleteCustomEquipment(deleted, replacement))

        assertEquals(before, database.trainingLoggedSetDao().getAll())
    }

    @Test
    @DisplayName("findOrCreate alias to a tombstoned seed row mints a new custom row")
    fun aliasToTombstonedSeedMintsNew() = runTest {
        val cableId = com.stratum.domain.lift.SeedEquipmentIds.Cable
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = cableId,
                name = "Cables",
                isCustom = false,
                isDeleted = true,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )

        val minted = equipmentRepository.findOrCreateByName("cable")

        assertNotEquals(cableId, minted)
        assertFalse(equipmentRepository.getEquipment(minted)!!.isDeleted)
        assertTrue(equipmentRepository.getEquipment(minted)!!.isCustom)
    }

    private suspend fun seedBodyweight() {
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = com.stratum.domain.lift.SeedEquipmentIds.Bodyweight,
                name = "Bodyweight",
                isCustom = false,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )
    }

    private suspend fun seedProgramWithSlots(equipmentId: EquipmentId) {
        database.programDao().upsert(
            com.stratum.core.infrastructure.db.ProgramEntity(
                id = com.stratum.domain.program.identity.ProgramId("p-1"),
                name = "Main",
                createdAt = "2026-01-01T00:00:00Z",
                cycleLength = 1,
                isActive = true,
            ),
        )
        database.workoutDao().upsertAll(
            listOf(
                com.stratum.core.infrastructure.db.WorkoutEntity(
                    id = com.stratum.domain.program.identity.WorkoutId("w-1"),
                    programId = com.stratum.domain.program.identity.ProgramId("p-1"),
                    name = "Push",
                    weekday = null,
                    position = 0,
                ),
            ),
        )
        database.workoutLiftDao().upsertAll(
            listOf(
                com.stratum.core.infrastructure.db.WorkoutLiftEntity(
                    id = com.stratum.domain.program.identity.WorkoutLiftId("wl-1"),
                    workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
                    liftId = LiftId("lift-a"),
                    equipmentId = equipmentId,
                    progressionScheme = com.stratum.domain.program.ProgressionScheme.Double,
                    position = 0,
                ),
                com.stratum.core.infrastructure.db.WorkoutLiftEntity(
                    id = com.stratum.domain.program.identity.WorkoutLiftId("wl-2"),
                    workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
                    liftId = LiftId("lift-b"),
                    equipmentId = equipmentId,
                    progressionScheme = com.stratum.domain.program.ProgressionScheme.Double,
                    position = 1,
                ),
                com.stratum.core.infrastructure.db.WorkoutLiftEntity(
                    id = com.stratum.domain.program.identity.WorkoutLiftId("wl-3"),
                    workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
                    liftId = LiftId("lift-b"),
                    equipmentId = null,
                    progressionScheme = com.stratum.domain.program.ProgressionScheme.Double,
                    position = 2,
                ),
            ),
        )
    }

    private suspend fun seedLift(
        id: LiftId,
        name: String,
        equipmentId: EquipmentId,
    ) {
        database.liftDao().upsert(
            LiftEntity(
                id = id,
                name = name,
                movementPatternsBitmask = setOf(MovementPattern.VerticalPull).toBitmask(),
                primaryMusclesBitmask = setOf(MuscleGroup.Back).toBitmask(),
                secondaryMusclesBitmask = 0,
                presetTags = emptySet<com.stratum.domain.lift.GymPreset>().toPresetCsv(),
                equipmentId = equipmentId,
                isCustom = false,
                enabled = true,
                muscleLengthBias = MuscleLengthBias.MidRange,
                fatigueProfile = FatigueProfile.Low,
                loadType = LoadType.Weighted,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )
    }
}
