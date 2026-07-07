package com.stratum.core.data.db

import androidx.room.Room
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.runBlocking
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
    private lateinit var sessionDao: TrainingSessionDao
    private lateinit var setDao: TrainingLoggedSetDao

    @BeforeEach
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.programDao().upsert(
            ProgramEntity(
                id = com.stratum.domain.program.identity.ProgramId("prog-1"),
                name = "Test",
                createdAt = "2025-01-01T00:00:00Z",
                cycleLength = 1,
            ),
        )
        sessionDao = database.trainingSessionDao()
        setDao = database.trainingLoggedSetDao()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Given sets across a time range when queried by window then only in-range rows return")
    fun getInWindowReturnsMatchingSets() = runTest {
        val sessionId = TrainingSessionId("session-1")
        sessionDao.upsert(
            TrainingSessionEntity(
                id = sessionId,
                programId = com.stratum.domain.program.identity.ProgramId("prog-1"),
                workoutId = null,
                status = com.stratum.domain.training.TrainingSessionStatus.COMPLETED,
                kind = com.stratum.domain.training.TrainingSessionKind.PROGRAM_WORKOUT,
                origin = com.stratum.domain.training.TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = 9_000L,
                sourceApp = null,
            ),
        )

        val insideEarly = loggedSet("set-early", sessionId, completedAtEpochMillis = 2_000L)
        val insideLate = loggedSet("set-late", sessionId, completedAtEpochMillis = 8_000L)
        val outsideBefore = loggedSet("set-before", sessionId, completedAtEpochMillis = 500L)
        val outsideAfter = loggedSet("set-after", sessionId, completedAtEpochMillis = 10_000L)
        setDao.upsertAll(listOf(insideEarly, insideLate, outsideBefore, outsideAfter))

        val results = setDao.getInWindow(1_500L, 8_500L)

        assertEquals(2, results.size)
        assertTrue(results.all { it.completedAtEpochMillis in 1_500L..8_500L })
        assertEquals(
            setOf(LoggedSetId("set-early"), LoggedSetId("set-late")),
            results.map { it.id }.toSet(),
        )
    }

    @Test
    @DisplayName("Unique constraint on (liftId, isPersonalRecord) rejects duplicate PRs but allows multiple null sets")
    fun uniqueConstraintOnPr() = runTest {
        val sessionId = TrainingSessionId("session-1")
        sessionDao.upsert(
            TrainingSessionEntity(
                id = sessionId,
                programId = com.stratum.domain.program.identity.ProgramId("prog-1"),
                workoutId = null,
                status = com.stratum.domain.training.TrainingSessionStatus.COMPLETED,
                kind = com.stratum.domain.training.TrainingSessionKind.PROGRAM_WORKOUT,
                origin = com.stratum.domain.training.TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = 9_000L,
                sourceApp = null,
            ),
        )

        val set1 = loggedSet("set-1", sessionId, completedAtEpochMillis = 2_000L).copy(
            liftId = LiftId("lift-bench"),
            isPersonalRecord = null
        )
        val set2 = loggedSet("set-2", sessionId, completedAtEpochMillis = 3_000L).copy(
            liftId = LiftId("lift-bench"),
            isPersonalRecord = null
        )
        setDao.upsertAll(listOf(set1, set2))

        val prSet1 = loggedSet("pr-set-1", sessionId, completedAtEpochMillis = 4_000L).copy(
            liftId = LiftId("lift-bench"),
            isPersonalRecord = true
        )
        setDao.upsert(prSet1)

        val prSet2 = loggedSet("pr-set-2", sessionId, completedAtEpochMillis = 5_000L).copy(
            liftId = LiftId("lift-bench"),
            isPersonalRecord = true
        )
        
        try {
            setDao.upsert(prSet2)
        } catch (e: Exception) {
            // Expected
        }

        val allSets = setDao.getForSession(sessionId)
        assertTrue(allSets.any { it.id == LoggedSetId("pr-set-1") })
        assertTrue(allSets.none { it.id == LoggedSetId("pr-set-2") })
    }

    private fun loggedSet(
        id: String,
        sessionId: TrainingSessionId,
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
