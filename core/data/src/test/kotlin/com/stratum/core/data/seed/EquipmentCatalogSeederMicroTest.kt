package com.stratum.core.data.seed

import androidx.room.Room
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class EquipmentCatalogSeederMicroTest {

    private lateinit var database: StratumDatabase
    private lateinit var subject: EquipmentCatalogSeeder

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        subject =
            EquipmentCatalogSeeder(
                equipmentDao = database.equipmentDao(),
                incrementTierDao = database.incrementTierDao(),
                loggingPort = FakeLoggingPort(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given a fixture with micro when seeded then stops land on the column")
    fun fixtureMicroSeeds() = runTest {
        val seeded =
            subject.seed(RuntimeEnvironment.getApplication(), MICRO_FIXTURE_PATH)

        assertEquals(2, seeded)
        val rows = database.equipmentDao().getAll().associateBy { it.id }
        assertEquals("[5.0,10.0]", rows[EquipmentId("pin-loaded-machine")]!!.microOptionsJson)
        assertNull(rows[EquipmentId("cable")]!!.microOptionsJson)
    }

    @Test
    @DisplayName("Given the bundled asset when read then no seed ships micro stops")
    fun shippedSeedsHaveNoMicro() {
        val text =
            RuntimeEnvironment.getApplication()
                .assets
                .open(EquipmentCatalogSeeder.ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val entries = Json.parseToJsonElement(text).jsonArray

        assertTrue(entries.isNotEmpty())
        entries.forEach { entry ->
            val micro = entry.jsonObject["microIncrements"]
            assertTrue(
                micro == null || micro.jsonArray.isEmpty(),
                "seed ${entry.jsonObject["id"]} must not ship micro stops",
            )
        }
    }

    private companion object {
        const val MICRO_FIXTURE_PATH = "lifts/seed-equipment-micro.json"
    }
}
