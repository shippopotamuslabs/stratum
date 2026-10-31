package com.stratum.core.infrastructure.sync

import androidx.room.Room
import com.stratum.core.infrastructure.db.EquipmentEntity
import com.stratum.core.infrastructure.db.IncrementTierEntity
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.seed.AssetLiftEquipmentCatalog
import com.stratum.core.infrastructure.seed.EquipmentCatalogSeeder
import com.stratum.core.infrastructure.seed.LiftCatalogSeeder
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
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
class UserOwnedDataClearerTest {
    private lateinit var database: StratumDatabase
    private lateinit var subject: UserOwnedDataClearer

    @BeforeEach
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database =
            Room.inMemoryDatabaseBuilder(context, StratumDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        subject =
            UserOwnedDataClearer(
                context = context,
                database = database,
                equipmentCatalogSeeder =
                    EquipmentCatalogSeeder(
                        equipmentDao = database.equipmentDao(),
                        incrementTierDao = database.incrementTierDao(),
                        loggingPort = FakeLoggingPort(),
                    ),
                liftCatalogSeeder =
                    LiftCatalogSeeder(
                        liftDao = database.liftDao(),
                        equipmentTagsByLiftId =
                            AssetLiftEquipmentCatalog(
                                context,
                                loggingPort = FakeLoggingPort(),
                            ).all(),
                        loggingPort = FakeLoggingPort(),
                    ),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("wipe drops custom lifts and increment tiers then reseeds catalog")
    fun wipeRemovesCustomRowsAndReseeds() = runTest {
        subject.clearUserOwnedData()
        val customId = LiftId("4be8acc5-4d83-4ccb-9477-2b8a61c15ec1")
        val equipmentId = EquipmentId("custom-stack-1")
        val tierId = IncrementTierId.forEquipmentBand(equipmentId, 0.0)
        database.liftDao().upsert(
            LiftEntity(
                id = customId,
                name = "Custom Press",
                movementPatternsBitmask = 1 shl MovementPattern.HorizontalPress.ordinal,
                primaryMusclesBitmask = 1 shl MuscleGroup.Chest.ordinal,
                secondaryMusclesBitmask = 0,
                presetTags = ",",
                isCustom = true,
                enabled = true,
            ),
        )
        database.equipmentDao().upsert(
            EquipmentEntity(
                id = equipmentId,
                name = "Custom Press Stack",
                isCustom = true,
            ),
        )
        database.incrementTierDao().upsertAll(
            listOf(
                IncrementTierEntity(
                    id = tierId,
                    equipmentId = equipmentId,
                    lowerBound = 0.0,
                    upperBound = null,
                    increment = 5.0,
                ),
            ),
        )

        subject.clearUserOwnedData()

        assertNull(database.liftDao().getById(customId))
        assertNull(database.incrementTierDao().getById(tierId))
        assertTrue(database.liftDao().countSeedLifts() > 10)
        assertTrue(database.equipmentDao().countSeedEquipment() > 0)
    }
}
