package com.stratum.core.data.sync
import com.stratum.core.infrastructure.sync.CloudDeleteQueue
import com.stratum.core.infrastructure.sync.SyncCollection

import androidx.room.Room
import com.stratum.core.infrastructure.db.StratumDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class CloudDeleteQueueReplaceIdsTest {
    private lateinit var database: StratumDatabase
    private lateinit var queue: CloudDeleteQueue

    @BeforeEach
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database =
            Room.inMemoryDatabaseBuilder(context, StratumDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        queue = CloudDeleteQueue(database.pendingCloudDeleteDao())
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `replaceIds enqueues removals and cancels kept ids`() = runBlocking {
        queue.enqueue(SyncCollection.LoggedSets, listOf("a", "b", "c"), nowMillis = 1L)
        queue.replaceIds(
            collection = SyncCollection.LoggedSets,
            previousIds = listOf("a", "b", "c"),
            nextIds = listOf("b", "d"),
            nowMillis = 2L,
        )
        val pending = database.pendingCloudDeleteDao().peekPending(20)
        val ids = pending.map { it.entityId }.toSet()
        assertTrue("a" in ids)
        assertTrue("c" in ids)
        assertTrue("b" !in ids) // canceled keep
        assertEquals(2, pending.size)
    }
}
