package com.stratum.core.data.db

import androidx.room.Room
import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class LoggedSetDaoContractTest {
    private lateinit var database: StratumDatabase
    private lateinit var sessionDao: HistoricalSessionDao
    private lateinit var setDao: LoggedSetDao

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        sessionDao = database.historicalSessionDao()
        setDao = database.loggedSetDao()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given sets across a time range when queried by window then only in-range rows return")
    fun getInWindowReturnsMatchingSets() = runTest {
        val sessionId = HistoricalSessionId("session-1")
        sessionDao.insertAll(
            listOf(
                HistoricalSessionEntity(
                    id = sessionId,
                    startedAtEpochMillis = 1_000L,
                    endedAtEpochMillis = 9_000L,
                    sourceApp = null,
                ),
            ),
        )

        val insideEarly = loggedSet("set-early", sessionId, completedAtEpochMillis = 2_000L)
        val insideLate = loggedSet("set-late", sessionId, completedAtEpochMillis = 8_000L)
        val outsideBefore = loggedSet("set-before", sessionId, completedAtEpochMillis = 500L)
        val outsideAfter = loggedSet("set-after", sessionId, completedAtEpochMillis = 10_000L)
        setDao.insertAll(listOf(insideEarly, insideLate, outsideBefore, outsideAfter))

        val results = setDao.getInWindow(1_500L, 8_500L)

        assertEquals(2, results.size)
        assertTrue(results.all { it.completedAtEpochMillis in 1_500L..8_500L })
        assertEquals(
            setOf(LoggedSetId("set-early"), LoggedSetId("set-late")),
            results.map { it.id }.toSet(),
        )
    }

    private fun loggedSet(
        id: String,
        sessionId: HistoricalSessionId,
        completedAtEpochMillis: Long,
    ): LoggedSetEntity = LoggedSetEntity(
        id = LoggedSetId(id),
        sessionId = sessionId,
        exerciseNameRaw = "Bench Press",
        liftId = LiftId("bench-press"),
        setIndex = 0,
        weightAmount = 135.0,
        weightUnit = "Lb",
        reps = 5,
        rir = 2,
        completedAtEpochMillis = completedAtEpochMillis,
    )
}
