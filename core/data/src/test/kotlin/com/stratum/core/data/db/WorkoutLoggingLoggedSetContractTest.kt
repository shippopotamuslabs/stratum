package com.stratum.core.data.db

import androidx.room.Room
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.identity.LiftId
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class WorkoutLoggingLoggedSetContractTest {
    private lateinit var database: StratumDatabase
    private lateinit var setDao: TrainingLoggedSetDao

    @BeforeEach
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        setDao = database.trainingLoggedSetDao()
        database.programDao().upsert(
            ProgramEntity(
                id = ProgramId("prog-1"),
                name = "Test",
                createdAt = "2025-01-01T00:00:00Z",
                cycleLength = 1,
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
        database.trainingSessionDao().upsert(
            TrainingSessionEntity(
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
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("WL-002 LoggedSetEntity persists lift snapshot columns")
    fun wl002_loggedSetEntitySnapshotColumns() = runTest {
        val entity = LoggedSetEntity(
            id = LoggedSetId("set-1"),
            sessionId = TrainingSessionId("ts-001"),
            exerciseNameRaw = "Bench Press",
            liftId = LiftId("lift-bench"),
            setIndex = 0,
            weightAmount = 135.0,
            weightUnit = "Lb",
            reps = 8,
            rir = 2,
            completedAtEpochMillis = 1_700_001_000_000L,
            liftName = "Barbell Bench Press",
            movementPatternsBitmask = WorkoutLoggingFixtures.BENCH_HORIZONTAL_BITMASK,
            primaryMusclesBitmask = WorkoutLoggingFixtures.CHEST_BITMASK,
            secondaryMusclesBitmask = WorkoutLoggingFixtures.TRICEPS_BITMASK,
            fatigueProfile = FatigueProfile.Medium,
            muscleLengthBias = MuscleLengthBias.MidRange,
            workoutLiftId = null,
            progressionScheme = null,
        )
        setDao.upsert(entity)

        val read = setDao.getForSession(TrainingSessionId("ts-001")).single()

        assertEquals("Barbell Bench Press", read.liftName)
        assertEquals(WorkoutLoggingFixtures.BENCH_HORIZONTAL_BITMASK, read.movementPatternsBitmask)
        assertEquals(WorkoutLoggingFixtures.CHEST_BITMASK, read.primaryMusclesBitmask)
        assertEquals(WorkoutLoggingFixtures.TRICEPS_BITMASK, read.secondaryMusclesBitmask)
        assertNull(read.workoutLiftId)
    }

    @Test
    @DisplayName("LoggedSetEntity persists partialReps")
    fun persistsPartialReps() = runTest {
        val entity = LoggedSetEntity(
            id = LoggedSetId("set-partial"),
            sessionId = TrainingSessionId("ts-001"),
            exerciseNameRaw = "Bench Press",
            liftId = LiftId("lift-bench"),
            setIndex = 0,
            weightAmount = 185.0,
            weightUnit = "Lb",
            reps = 5,
            rir = 1,
            partialReps = 2,
            completedAtEpochMillis = 1_700_001_000_000L,
            liftName = "Barbell Bench Press",
        )
        setDao.upsert(entity)

        val read = setDao.getForSession(TrainingSessionId("ts-001")).single()
        assertEquals(2, read.partialReps)
        assertEquals(5, read.reps)
    }
}
