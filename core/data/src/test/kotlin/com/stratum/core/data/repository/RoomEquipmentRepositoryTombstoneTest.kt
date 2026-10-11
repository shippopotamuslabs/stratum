package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.data.db.EquipmentEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.mapper.toBitmask
import com.stratum.core.data.mapper.toPresetCsv
import com.stratum.core.data.sync.SyncFlags
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
    @DisplayName("lifts keep resolving the tombstoned equipment name (history display)")
    fun liftJoinStillResolvesTombstonedName() = runTest {
        val id = equipmentRepository.addCustomEquipment(name = "Old Stack")
        val liftId = LiftId("lat-pulldown")
        seedLift(liftId, "Lat pulldown", id)

        assertTrue(equipmentRepository.deleteCustomEquipment(id))

        val lifts = liftRepository.observeLifts().first()
        val lift = lifts.first { it.id == liftId }
        assertEquals(id, lift.equipmentId)
        assertEquals("Old Stack", lift.equipmentName)
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
    @DisplayName("seed equipment cannot be deleted")
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

        assertFalse(equipmentRepository.deleteCustomEquipment(seedId))
        assertFalse(database.equipmentDao().getById(seedId)!!.isDeleted)
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
