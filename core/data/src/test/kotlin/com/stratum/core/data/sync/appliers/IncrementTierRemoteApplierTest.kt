package com.stratum.core.data.sync.appliers

import androidx.room.Room
import com.stratum.core.infrastructure.db.EquipmentEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
class IncrementTierRemoteApplierTest {
    private lateinit var database: StratumDatabase
    private lateinit var subject: IncrementTierRemoteApplier

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        subject =
            IncrementTierRemoteApplier(
                incrementTierDao = database.incrementTierDao(),
                equipmentDao = database.equipmentDao(),
                decoders = SyncEntityDecoders(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("lift-scoped doc is skipped: per-lift overrides were removed")
    fun liftScopedDocSkipped() = runTest {
        val liftId = "4be8acc5-4d83-4ccb-9477-2b8a61c15ec1"
        val tierId = IncrementTierId.forBand(LiftId(liftId), 0.0).value
        val ok =
            subject.upsertFromRemote(
                tierId,
                mapOf(
                    "lift_id" to liftId,
                    "lower_bound" to 0.0,
                    "increment" to 5.0,
                    FirestoreSyncMetadata.UPDATED_AT to 10L,
                ),
            )
        assertTrue(ok)
        assertNull(database.incrementTierDao().getById(IncrementTierId(tierId)))
    }

    @Test
    @DisplayName("equipment-scoped doc with missing equipment is skipped without inserting")
    fun missingParentEquipmentSkipped() = runTest {
        val equipmentId = EquipmentId("eq-missing")
        val tierId = IncrementTierId.forEquipmentBand(equipmentId, 0.0).value
        val ok =
            subject.upsertFromRemote(
                tierId,
                mapOf(
                    "equipment_id" to equipmentId.value,
                    "lower_bound" to 0.0,
                    "increment" to 5.0,
                    FirestoreSyncMetadata.UPDATED_AT to 10L,
                ),
            )
        assertTrue(ok)
        assertNull(database.incrementTierDao().getById(IncrementTierId(tierId)))
    }

    @Test
    @DisplayName("equipment-scoped doc with existing equipment is upserted")
    fun parentEquipmentPresentInserts() = runTest {
        val equipmentId = EquipmentId("eq-1")
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = equipmentId,
                name = "Barbell",
                isCustom = false,
            ),
        )
        val tierId = IncrementTierId.forEquipmentBand(equipmentId, 0.0).value
        val ok =
            subject.upsertFromRemote(
                tierId,
                mapOf(
                    "equipment_id" to equipmentId.value,
                    "lower_bound" to 0.0,
                    "increment" to 5.0,
                    FirestoreSyncMetadata.UPDATED_AT to 10L,
                ),
            )
        assertTrue(ok)
        val row = database.incrementTierDao().getById(IncrementTierId(tierId))
        assertNotNull(row)
        assertEquals(5.0, row!!.increment)
    }
}
