package com.stratum.core.infrastructure.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lift catalog DAO against real on-device SQLite. The Robolectric DAO contract tests cover
 * query logic; this suite exists to catch what Robolectric shadows hide — real SQLite
 * behavior for bitmask predicates, upserts, and type converters on device.
 */
@RunWith(AndroidJUnit4::class)
class LiftDaoDeviceTest {

    private lateinit var database: StratumDatabase
    private lateinit var dao: LiftDao

    @Before
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = database.liftDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertAndReadBackOnRealSqlite() = runBlocking {
        val lift =
            LiftEntity(
                id = LiftId("device-test-bench"),
                name = "Device Test Bench",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = ",",
                isCustom = true,
                enabled = true,
            )

        dao.upsert(lift)

        val read = dao.getById(LiftId("device-test-bench"))
        assertNotNull(read)
        assertEquals("Device Test Bench", read?.name)
        assertEquals(1, dao.countAll())
        assertEquals(1, dao.enabledCount())
    }

    @Test
    fun setEnabledFlipsAvailabilityOnDevice() = runBlocking {
        val lift =
            LiftEntity(
                id = LiftId("device-test-row"),
                name = "Device Test Row",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = ",",
                isCustom = true,
                enabled = true,
            )
        dao.upsert(lift)

        dao.setEnabled(LiftId("device-test-row"), enabled = false, updatedAtEpochMillis = 1L)

        assertEquals(0, dao.enabledCount())
        assertEquals(1, dao.countAll())
    }
}
