package com.stratum.core.data.repository

import androidx.room.Room
import com.stratum.core.infrastructure.db.EquipmentEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.core.data.sync.testSyncTouch
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
@DisplayName("RoomEquipmentRepository.renameAndSetIncrementTiers")
class RoomEquipmentRepositoryRenameTiersTest {

    private val lb = Weight.WeightUnit.Lb
    private lateinit var database: StratumDatabase
    private lateinit var equipmentRepository: RoomEquipmentRepository

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
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given seeded equipment when rename+tiers runs then name, tiers, and stops land together")
    fun renameAndTiersLandTogether() = runTest {
        val id = EquipmentId("cable")
        seedEquipment(id, name = "Cables")
        equipmentRepository.setIncrementTiers(
            id,
            listOf(IncrementTier(Weight(0.0, lb), null, Weight(15.0, lb))),
            listOf(Weight(5.0, lb)),
        )

        val newTiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb)))
        equipmentRepository.renameAndSetIncrementTiers(
            id,
            "Lat stack",
            newTiers,
            listOf(Weight(2.5, lb)),
        )

        val stored = equipmentRepository.getEquipment(id)
        assertNotNull(stored)
        assertEquals("Lat stack", stored!!.name)
        assertEquals(newTiers, stored.incrementTiers)
        assertEquals(listOf(Weight(2.5, lb)), stored.microIncrements)
    }

    @Test
    @DisplayName("Given a tombstoned row when rename+tiers runs then nothing changes")
    fun tombstonedRowIsNoOp() = runTest {
        val id = EquipmentId("gone")
        seedEquipment(id, name = "Gone", isDeleted = true)

        equipmentRepository.renameAndSetIncrementTiers(
            id,
            "Back again",
            listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb))),
            emptyList(),
        )

        val stored = equipmentRepository.getEquipment(id)
        assertNotNull(stored)
        assertEquals("Gone", stored!!.name)
        assertEquals(emptyList<IncrementTier>(), stored.incrementTiers)
    }

    private suspend fun seedEquipment(
        id: EquipmentId,
        name: String,
        isDeleted: Boolean = false,
    ) {
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = id,
                name = name,
                isCustom = false,
                updatedAtEpochMillis = 1L,
                pendingSync = SyncFlags.CLEAN,
                isDeleted = isDeleted,
            ),
        )
    }
}
