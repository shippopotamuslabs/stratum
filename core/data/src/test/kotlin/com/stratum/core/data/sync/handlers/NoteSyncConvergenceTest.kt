package com.stratum.core.data.sync.handlers

import androidx.room.Room
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingSessionEntity
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.appliers.LiftRemoteApplier
import com.stratum.core.data.sync.appliers.LoggedSetRemoteApplier
import com.stratum.core.data.sync.appliers.ProgramRemoteApplier
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.core.data.sync.codecs.SyncEntityEncoders
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * End-to-end handler convergence for the note collections: remote documents hydrate
 * into ordered rows, local writes surface as pending payloads, cleanup clears them,
 * and deletes remove them.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class NoteSyncConvergenceTest {

    private lateinit var database: StratumDatabase
    private lateinit var registry: SyncHandlerRegistry
    private val encoders = SyncEntityEncoders()

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        val decoders = SyncEntityDecoders()
        val provider =
            SyncHandlersProvider(
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
                liftRemoteApplier = LiftRemoteApplier(database.liftDao(), encoders, decoders),
                programRemoteApplier = ProgramRemoteApplier(database.programDao(), decoders),
                loggedSetRemoteApplier = LoggedSetRemoteApplier(database.trainingLoggedSetDao(), decoders),
            )
        registry = SyncHandlerRegistry(provider)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("catalog note hydrate, pending payload, cleanup, and delete converge")
    fun catalogNotesConverge() = runTest {
        seedLift("lift-1")
        val handler = registry.get(SyncCollection.LiftCatalogNotes)

        assertTrue(
            handler.upsertFromRemote(
                "c-1",
                mapOf(
                    "lift_id" to "lift-1",
                    "body" to "remote pin",
                    "position" to 3,
                    "created_at_epoch_millis" to 30L,
                    FirestoreSyncMetadata.UPDATED_AT to 40L,
                ),
            ),
        )
        val hydrated = database.liftNoteDao().getForLift(LiftId("lift-1")).single()
        assertEquals("remote pin", hydrated.body)
        assertEquals(3, hydrated.position)
        assertEquals(30L, hydrated.createdAtEpochMillis)

        // Hydrate lands CLEAN; a local edit makes the row pending with full payload.
        database.liftNoteDao().upsertAll(
            listOf(
                hydrated.copy(
                    body = "local edit",
                    updatedAtEpochMillis = 50L,
                    pendingSync = SyncFlags.PENDING_UPSERT,
                ),
            ),
        )
        val pending = handler.pendingRows()
        assertEquals(listOf("c-1"), pending.map { it.id })
        assertEquals(3, pending.single().payload["position"])
        assertEquals("local edit", pending.single().payload["body"])

        handler.markSyncedIfUnchanged("c-1", 50L)
        assertTrue(handler.pendingRows().isEmpty())

        handler.deleteLocal("c-1")
        assertTrue(database.liftNoteDao().getForLift(LiftId("lift-1")).isEmpty())
    }

    @Test
    @DisplayName("hydrated session notes keep their submitted order")
    fun sessionNotesKeepOrder() = runTest {
        seedSession("ts-1")
        val handler = registry.get(SyncCollection.LiftSessionNotes)

        assertTrue(
            handler.upsertFromRemote(
                "n-2",
                mapOf(
                    "session_id" to "ts-1",
                    "lift_id" to "lift-1",
                    "workout_lift_id" to "wl-1",
                    "body" to "second",
                    "position" to 1,
                    "created_at_epoch_millis" to 20L,
                    FirestoreSyncMetadata.UPDATED_AT to 21L,
                ),
            ),
        )
        assertTrue(
            handler.upsertFromRemote(
                "n-1",
                mapOf(
                    "session_id" to "ts-1",
                    "lift_id" to "lift-1",
                    "workout_lift_id" to "wl-1",
                    "body" to "first",
                    "position" to 0,
                    "created_at_epoch_millis" to 10L,
                    FirestoreSyncMetadata.UPDATED_AT to 11L,
                ),
            ),
        )

        assertEquals(
            listOf("first", "second"),
            database.liftSessionNoteDao()
                .getNotesForSlot(
                    TrainingSessionId("ts-1"),
                    WorkoutLiftId("wl-1"),
                ).map { it.body },
        )
    }

    private suspend fun seedSession(id: String) {
        database.trainingSessionDao().upsert(
            TrainingSessionEntity(
                id = TrainingSessionId(id),
                programId = null,
                workoutId = null,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAtEpochMillis = 10L,
                endedAtEpochMillis = 11L,
                sourceApp = null,
                workoutNameSnapshot = null,
            ),
        )
    }

    private suspend fun seedLift(id: String) {
        database.liftDao().upsert(
            LiftEntity(
                id = LiftId(id),
                name = id,
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = "",
                isCustom = false,
                enabled = true,
            ),
        )
    }
}
