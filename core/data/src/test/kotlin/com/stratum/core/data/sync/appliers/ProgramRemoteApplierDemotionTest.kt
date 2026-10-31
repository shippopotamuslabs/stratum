package com.stratum.core.data.sync.appliers

import androidx.room.Room
import com.stratum.core.infrastructure.db.ProgramEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.domain.program.identity.ProgramId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
class ProgramRemoteApplierDemotionTest {
    private lateinit var database: StratumDatabase
    private lateinit var subject: ProgramRemoteApplier

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        subject = ProgramRemoteApplier(database.programDao(), SyncEntityDecoders())
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("remote active program demotes other actives as PENDING_UPSERT for cloud heal")
    fun demotionMarksPending() = runTest {
        val dao = database.programDao()
        dao.upsert(
            ProgramEntity(
                id = ProgramId("p1"),
                name = "Local Active",
                createdAt = "2020-01-01",
                cycleLength = 7,
                isActive = true,
                updatedAtEpochMillis = 100L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )

        val ok =
            subject.upsertFromRemote(
                id = "p2",
                data =
                    mapOf(
                        "name" to "Remote Active",
                        "created_at" to "2020-02-01",
                        "cycle_length" to 7,
                        "is_active" to true,
                        "training_weekdays" to emptyList<String>(),
                        "current_cycle_number" to 1,
                        "cycle_position" to 0,
                        FirestoreSyncMetadata.UPDATED_AT to 500L,
                    ),
            )
        assertTrue(ok)

        val p1 = dao.getById(ProgramId("p1"))!!
        assertFalse(p1.isActive)
        assertEquals(SyncFlags.PENDING_UPSERT, p1.pendingSync)
        assertTrue(p1.updatedAtEpochMillis >= 500L)

        val p2 = dao.getById(ProgramId("p2"))!!
        assertTrue(p2.isActive)
        assertEquals(SyncFlags.CLEAN, p2.pendingSync)
    }

    @Test
    @DisplayName("inactive remote program does not demote local actives")
    fun inactiveRemoteDoesNotDemote() = runTest {
        val dao = database.programDao()
        dao.upsert(
            ProgramEntity(
                id = ProgramId("p1"),
                name = "Local Active",
                createdAt = "2020-01-01",
                cycleLength = 7,
                isActive = true,
                updatedAtEpochMillis = 100L,
                pendingSync = SyncFlags.CLEAN,
            ),
        )

        val ok =
            subject.upsertFromRemote(
                id = "p2",
                data =
                    mapOf(
                        "name" to "Library",
                        "created_at" to "2020-02-01",
                        "cycle_length" to 7,
                        "is_active" to false,
                        "training_weekdays" to emptyList<String>(),
                        FirestoreSyncMetadata.UPDATED_AT to 500L,
                    ),
            )
        assertTrue(ok)

        val p1 = dao.getById(ProgramId("p1"))!!
        assertTrue(p1.isActive)
        assertEquals(SyncFlags.CLEAN, p1.pendingSync)
    }
}
