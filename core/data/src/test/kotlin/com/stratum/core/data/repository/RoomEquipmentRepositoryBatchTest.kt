package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.data.db.EquipmentEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.mapper.toBitmask
import com.stratum.core.data.mapper.toPresetCsv
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.testSyncTouch
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.FatigueProfile
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
@DisplayName("RoomEquipmentRepository.createCustomEquipmentAndAssignLifts")
class RoomEquipmentRepositoryBatchTest {

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
    @DisplayName(
        "Given signal lifts when batch create+assign runs then equipment and lift FKs land together",
    )
    fun createAndAssignIsAtomic() = runTest {
        val seedCable = EquipmentId("cable")
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = seedCable,
                name = "Cables",
                isCustom = false,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )
        val latId = LiftId("lat-pulldown")
        val rowId = LiftId("cable-row")
        seedLift(latId, "Lat pulldown", seedCable)
        seedLift(rowId, "Cable row", seedCable)

        val tiers =
            listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb)))
        val newId =
            equipmentRepository.createCustomEquipmentAndAssignLifts(
                name = "Lat pulldown stack",
                tiers = tiers,
                liftIds = listOf(latId),
            )

        val created = equipmentRepository.getEquipment(newId)
        assertNotNull(created)
        assertEquals("Lat pulldown stack", created!!.name)
        assertTrue(created.isCustom)
        assertEquals(1, created.incrementTiers.size)
        assertEquals(2.5, created.incrementTiers.single().increment.amount)

        val lifts = liftRepository.observeLifts().first()
        assertEquals(newId, lifts.first { it.id == latId }.equipmentId)
        assertEquals(seedCable, lifts.first { it.id == rowId }.equipmentId)
        assertTrue(lifts.first { it.id == latId }.usesEquipmentDefaultIncrements)
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
