package com.stratum.core.infrastructure.db

import androidx.room.Room
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
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
class TrainingSessionEntityContractTest {
    private lateinit var database: StratumDatabase
    private lateinit var sessionDao: TrainingSessionDao

    @BeforeEach
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        sessionDao = database.trainingSessionDao()
        database.programDao().upsert(
            ProgramEntity(
                id = ProgramId("prog-1"),
                name = "Test",
                createdAt = "2025-01-01T00:00:00Z",
                cycleLength = 2,
            ),
        )
        database.workoutDao().upsertAll(
            listOf(
                WorkoutEntity(
                id = WorkoutId("wkt-a"),
                programId = ProgramId("prog-1"),
                name = "A",
                weekday = Weekday.Monday,
                position = 0,
                ),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("WL-001 TrainingSessionEntity persists renamed table with extended columns")
    fun wl001_trainingSessionEntityRoundTrip() = runTest {
        val entity = TrainingSessionEntity(
            id = TrainingSessionId("ts-001"),
            programId = ProgramId("prog-1"),
            workoutId = WorkoutId("wkt-a"),
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.IMPORT,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = Weekday.Monday,
            startedAtEpochMillis = 1_700_000_000_000L,
            endedAtEpochMillis = 1_700_003_600_000L,
            sourceApp = "liftlab",
        )
        sessionDao.upsert(entity)

        val read = sessionDao.getById(TrainingSessionId("ts-001"))

        assertEquals(entity, read)
        val tableNames = database.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='training_sessions'",
        ).use { it.moveToFirst() }
        assertTrue(tableNames)
    }
}
