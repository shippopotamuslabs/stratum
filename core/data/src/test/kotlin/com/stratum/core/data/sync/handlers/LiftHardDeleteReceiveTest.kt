package com.stratum.core.data.sync.handlers

import androidx.room.Room
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.db.ProgramEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.db.WorkoutEntity
import com.stratum.core.infrastructure.db.WorkoutLiftEntity
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.appliers.LiftRemoteApplier
import com.stratum.core.data.sync.appliers.LoggedSetRemoteApplier
import com.stratum.core.data.sync.appliers.ProgramRemoteApplier
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.core.data.sync.codecs.SyncEntityEncoders
import com.stratum.core.data.sync.testSyncTouch
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
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

/**
 * STR-131 Scope 5: a received hard delete for a lift (merge custom source on
 * another device) must not FK-RESTRICT-fail on a device whose templates still
 * slot the lift — the handler sweeps local template references first.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LiftHardDeleteReceiveTest {

    private lateinit var database: StratumDatabase
    private lateinit var liftsHandler: SyncCollectionHandler

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        val encoders = SyncEntityEncoders()
        val decoders = SyncEntityDecoders()
        val provider =
            SyncHandlersProvider(
                database = database,
                syncTouch = testSyncTouch(RuntimeEnvironment.getApplication(), database.pendingCloudDeleteDao()),
                userProfileDao = database.userProfileDao(),
                onboardingDao = database.onboardingProgressDao(),
                gymDao = database.gymPreferenceDao(),
                specializationDao = database.specializationPreferenceDao(),
                equipmentDao = database.equipmentDao(),
                liftDao = database.liftDao(),
                incrementTierDao = database.incrementTierDao(),
                programDao = database.programDao(),
                workoutDao = database.workoutDao(),
                workoutLiftDao = database.workoutLiftDao(),
                liftSetDao = database.liftSetDao(),
                sessionDao = database.trainingSessionDao(),
                liftSessionNoteDao = database.liftSessionNoteDao(),
                liftNoteDao = database.liftNoteDao(),
                workoutLiftNoteDao = database.workoutLiftNoteDao(),
                loggedSetDao = database.trainingLoggedSetDao(),
                programChangeEventDao = database.programChangeEventDao(),
                encoders = encoders,
                decoders = decoders,
                liftRemoteApplier = LiftRemoteApplier(database, database.liftDao(), encoders, decoders),
                programRemoteApplier = ProgramRemoteApplier(database.programDao(), decoders),
                loggedSetRemoteApplier = LoggedSetRemoteApplier(database.trainingLoggedSetDao(), decoders),
            )
        liftsHandler =
            SyncHandlerRegistry(provider).inHydrateOrder()
                .single { it.collection == SyncCollection.Lifts }
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Received hard delete with local template slots sweeps them instead of failing")
    fun hardDeleteWithLocalSlots() = runTest {
        database.liftDao().upsert(
            LiftEntity(
                id = LiftId("lift-gone"),
                name = "Gone",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = ",",
                isCustom = true,
                enabled = true,
            ),
        )
        database.programDao().upsert(
            ProgramEntity(
                id = ProgramId("p-1"),
                name = "Main",
                createdAt = "2026-01-01T00:00:00Z",
                cycleLength = 1,
                isActive = true,
            ),
        )
        database.workoutDao().upsertAll(
            listOf(WorkoutEntity(WorkoutId("w-1"), ProgramId("p-1"), "Push", null, 0)),
        )
        database.workoutLiftDao().upsertAll(
            listOf(
                WorkoutLiftEntity(
                    id = WorkoutLiftId("wl-1"),
                    workoutId = WorkoutId("w-1"),
                    liftId = LiftId("lift-gone"),
                    progressionScheme = ProgressionScheme.Linear,
                    position = 0,
                ),
            ),
        )

        // Must not throw SQLiteConstraintException (FK RESTRICT on workout_lifts).
        liftsHandler.deleteLocal("lift-gone")

        assertNull(database.liftDao().getById(LiftId("lift-gone")))
        assertTrue(database.workoutLiftDao().getAll().isEmpty())
    }
}
