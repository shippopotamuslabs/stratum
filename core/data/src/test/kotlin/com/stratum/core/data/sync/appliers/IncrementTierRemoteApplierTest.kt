package com.stratum.core.data.sync.appliers

import androidx.room.Room
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
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
                liftDao = database.liftDao(),
                equipmentDao = database.equipmentDao(),
                decoders = SyncEntityDecoders(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("missing parent lift is skipped without inserting")
    fun missingParentLiftSkipped() = runTest {
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
    @DisplayName("tier with local parent lift is upserted")
    fun parentPresentInserts() = runTest {
        val liftId = LiftId("custom-1")
        database.liftDao().upsert(
            LiftEntity(
                id = liftId,
                name = "Custom",
                movementPatternsBitmask = 1 shl MovementPattern.HorizontalPress.ordinal,
                primaryMusclesBitmask = 1 shl MuscleGroup.Chest.ordinal,
                secondaryMusclesBitmask = 0,
                presetTags = ",",
                isCustom = true,
                enabled = true,
            ),
        )
        val tierId = IncrementTierId.forBand(liftId, 0.0).value
        val ok =
            subject.upsertFromRemote(
                tierId,
                mapOf(
                    "lift_id" to liftId.value,
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
