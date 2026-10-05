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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
@DisplayName("Room micro (adder) persistence")
class RoomMicroIncrementsTest {

    private val lb = Weight.WeightUnit.Lb
    private val pinTiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(15.0, lb)))
    private val pinMicro = listOf(Weight(5.0, lb), Weight(10.0, lb))
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
    @DisplayName("Given tiers with micro when set then equipment reads back stops")
    fun equipmentMicroPersists() = runTest {
        val id = EquipmentId("pin-loaded-machine")
        seedEquipment(id)

        equipmentRepository.setIncrementTiers(id, pinTiers, pinMicro)

        val read = equipmentRepository.getEquipment(id)!!
        assertEquals(listOf(5.0, 10.0), read.microIncrements.map { it.amount })
    }

    @Test
    @DisplayName("Given empty micro when set then equipment reads back no adder")
    fun emptyEquipmentMicroClears() = runTest {
        val id = EquipmentId("pin-loaded-machine")
        seedEquipment(id)
        equipmentRepository.setIncrementTiers(id, pinTiers, pinMicro)

        equipmentRepository.setIncrementTiers(id, pinTiers, emptyList())

        assertEquals(emptyList<Weight>(), equipmentRepository.getEquipment(id)!!.microIncrements)
    }

    @Test
    @DisplayName("Given stored stops when tiers rewritten without micro then stops are preserved")
    fun tiersOnlyWritePreservesEquipmentMicro() = runTest {
        val id = EquipmentId("pin-loaded-machine")
        seedEquipment(id)
        equipmentRepository.setIncrementTiers(id, pinTiers, pinMicro)

        equipmentRepository.setIncrementTiers(id, pinTiers)

        assertEquals(listOf(5.0, 10.0), equipmentRepository.getEquipment(id)!!.microIncrements.map { it.amount })
    }

    @Test
    @DisplayName("Given equipment micro when lift inherits then effective micro resolves")
    fun inheritedMicroResolves() = runTest {
        val equipmentId = EquipmentId("pin-loaded-machine")
        val liftId = LiftId("lat-pulldown")
        seedEquipment(equipmentId)
        seedLift(liftId, equipmentId)
        equipmentRepository.setIncrementTiers(equipmentId, pinTiers, pinMicro)

        val read = liftRepository.getLiftsByIds(listOf(liftId)).single()

        assertEquals(listOf(5.0, 10.0), read.microIncrements.map { it.amount })
        assertEquals(true, read.usesEquipmentDefaultMicro)
    }

    @Test
    @DisplayName("Given stale lift micro when equipment reassigned then micro clears and lift re-inherits")
    fun reassignClearsStaleMicro() = runTest {
        val fromId = EquipmentId("pin-loaded-machine")
        val toId = EquipmentId("cable")
        val liftId = LiftId("lat-pulldown")
        seedEquipment(fromId)
        seedEquipment(toId)
        seedLift(liftId, fromId)
        // No repository write path remains (STR-84); seed stale pre-migration data directly.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE lifts SET micro_options_json = '[2.5]' WHERE id = '${liftId.value}'",
        )

        liftRepository.setLiftEquipment(liftId, toId, LoadType.Weighted)

        val read = liftRepository.getLiftsByIds(listOf(liftId)).single()
        assertEquals(emptyList<Weight>(), read.microIncrements)
        assertEquals(true, read.usesEquipmentDefaultMicro)
    }

    private suspend fun seedEquipment(id: EquipmentId) {
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = id,
                name = "Machine",
                isCustom = true,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )
    }

    private suspend fun seedLift(id: LiftId, equipmentId: EquipmentId) {
        database.liftDao().upsert(
            LiftEntity(
                id = id,
                name = "Lift",
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
