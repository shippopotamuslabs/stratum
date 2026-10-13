package com.stratum.core.data.sync.handlers

import androidx.room.Room
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.LiftSessionNoteEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingSessionEntity
import com.stratum.core.data.sync.CloudDeleteQueue
import com.stratum.core.data.sync.DefaultCloudSyncFeatureGate
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.core.data.sync.UserOwnedWriteCoordinator
import com.stratum.core.data.sync.appliers.LiftRemoteApplier
import com.stratum.core.data.sync.appliers.LoggedSetRemoteApplier
import com.stratum.core.data.sync.appliers.ProgramRemoteApplier
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.core.data.sync.codecs.SyncEntityEncoders
import com.stratum.core.data.work.CloudSyncScheduler
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
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
    private lateinit var writeCoordinator: UserOwnedWriteCoordinator
    private val encoders = SyncEntityEncoders()

    @BeforeEach
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StratumDatabase::class.java,
        ).allowMainThreadQueries().build()
        val decoders = SyncEntityDecoders()
        // One coordinator shared with the provider so tests can hold the engine-side
        // lock exactly like the pull engine and conflict adoption do.
        writeCoordinator = UserOwnedWriteCoordinator()
        val provider =
            SyncHandlersProvider(
                database = database,
                syncTouch = SyncTouch(
                    cloudDeleteQueue = CloudDeleteQueue(database.pendingCloudDeleteDao()),
                    cloudSyncScheduler = CloudSyncScheduler(RuntimeEnvironment.getApplication()),
                    featureGate = DefaultCloudSyncFeatureGate().apply { setEnabledForTests(false) },
                    writeCoordinator = writeCoordinator,
                ),
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
    @DisplayName("remote note delete recomputes the parent mirror from remaining rows")
    fun noteDeleteRefreshesMirror() = runTest {
        seedLift("lift-1")
        val handler = registry.get(SyncCollection.LiftCatalogNotes)
        handler.upsertFromRemote(
            "c-1",
            mapOf(
                "lift_id" to "lift-1",
                "body" to "first",
                "position" to 0,
                "created_at_epoch_millis" to 30L,
                FirestoreSyncMetadata.UPDATED_AT to 40L,
            ),
        )
        handler.upsertFromRemote(
            "c-2",
            mapOf(
                "lift_id" to "lift-1",
                "body" to "second",
                "position" to 1,
                "created_at_epoch_millis" to 31L,
                FirestoreSyncMetadata.UPDATED_AT to 41L,
            ),
        )
        database.liftDao().setPinnedNote(LiftId("lift-1"), "first", 40L)

        // Deleting one row refreshes the mirror to the remaining first body.
        handler.deleteLocal("c-1")
        assertEquals("second", database.liftDao().getById(LiftId("lift-1"))?.pinnedNote)

        // Deleting the final row clears the mirror, so the lazy-migrate read
        // fallback cannot recreate the deleted note and re-upload it.
        handler.deleteLocal("c-2")
        assertNull(database.liftDao().getById(LiftId("lift-1"))?.pinnedNote)
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

    @Test
    @DisplayName("legacy slot document displaces per-note siblings with tombstones")
    fun legacySlotDocDisplacesSiblings() = runTest {
        seedSession("ts-1")
        val dao = database.liftSessionNoteDao()
        dao.upsertAll(
            listOf(
                LiftSessionNoteEntity(
                    id = LiftSessionNoteId("uuid-a"),
                    sessionId = TrainingSessionId("ts-1"),
                    liftId = LiftId("lift-1"),
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    body = "new ui note",
                    position = 0,
                    updatedAtEpochMillis = 5L,
                ),
                LiftSessionNoteEntity(
                    id = LiftSessionNoteId("uuid-b"),
                    sessionId = TrainingSessionId("ts-1"),
                    liftId = LiftId("lift-1"),
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    body = "second note",
                    position = 1,
                    updatedAtEpochMillis = 6L,
                ),
            ),
        )
        val handler = registry.get(SyncCollection.LiftSessionNotes)

        assertTrue(
            handler.upsertFromRemote(
                "ts-1::wl-1",
                mapOf(
                    "session_id" to "ts-1",
                    "lift_id" to "lift-1",
                    "workout_lift_id" to "wl-1",
                    "body" to "legacy whole slot",
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                ),
            ),
        )

        assertEquals(
            listOf("legacy whole slot"),
            dao.getNotesForSlot(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1")).map { it.body },
        )
        val tombstoned =
            database.pendingCloudDeleteDao().peekPending()
                .filter { it.collectionPath == SyncCollection.LiftSessionNotes.firestorePath }
                .map { it.entityId }
                .toSet()
        assertEquals(setOf("uuid-a", "uuid-b"), tombstoned)
    }

    @Test
    @DisplayName("per-note documents merge row-by-row without touching siblings")
    fun perNoteDocsMergeSurgically() = runTest {
        seedSession("ts-1")
        val dao = database.liftSessionNoteDao()
        dao.upsertAll(
            listOf(
                LiftSessionNoteEntity(
                    id = LiftSessionNoteId("uuid-a"),
                    sessionId = TrainingSessionId("ts-1"),
                    liftId = LiftId("lift-1"),
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    body = "existing",
                    position = 0,
                    updatedAtEpochMillis = 5L,
                ),
            ),
        )
        val handler = registry.get(SyncCollection.LiftSessionNotes)

        assertTrue(
            handler.upsertFromRemote(
                "uuid-b",
                mapOf(
                    "session_id" to "ts-1",
                    "lift_id" to "lift-1",
                    "workout_lift_id" to "wl-1",
                    "body" to "arriving",
                    "position" to 1,
                    "created_at_epoch_millis" to 20L,
                    FirestoreSyncMetadata.UPDATED_AT to 21L,
                ),
            ),
        )

        assertEquals(
            listOf("existing", "arriving"),
            dao.getNotesForSlot(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1")).map { it.body },
        )
        assertTrue(database.pendingCloudDeleteDao().peekPending().isEmpty())
    }

    @Test
    @DisplayName("legacy apply under the engine-held lock completes without deadlock")
    fun legacyApplyUnderHeldLock() =
        // Real time on purpose: runTest's virtual clock fast-forwards while Room's real
        // threads are in flight, which would trip a virtual withTimeout immediately.
        runBlocking {
            seedSession("ts-1")
            val handler = registry.get(SyncCollection.LiftSessionNotes)
            val legacyDoc =
                mapOf(
                    "session_id" to "ts-1",
                    "lift_id" to "lift-1",
                    "workout_lift_id" to "wl-1",
                    "body" to "legacy whole slot",
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                )

            // Both production callers (pull engine, conflict adoption) hold the coordinator
            // lock around upsertFromRemote; the handler must not reacquire it (Mutex is
            // non-reentrant). A nested acquisition would hang here until the timeout.
            try {
                withTimeout(10_000) {
                    writeCoordinator.withWriteLock {
                        assertTrue(handler.upsertFromRemote("ts-1::wl-1", legacyDoc))
                    }
                }
            } catch (_: TimeoutCancellationException) {
                fail("legacy slot apply deadlocked under the engine-held write lock")
            }

            assertEquals(
                listOf("legacy whole slot"),
                database.liftSessionNoteDao()
                    .getNotesForSlot(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1"))
                    .map { it.body },
            )
        }

    @Test
    @DisplayName("pending newer sibling is preserved and the losing legacy doc is tombstoned")
    fun pendingNewerSiblingPreserved() = runTest {
        seedSession("ts-1")
        val dao = database.liftSessionNoteDao()
        dao.upsertAll(
            listOf(
                LiftSessionNoteEntity(
                    id = LiftSessionNoteId("uuid-local"),
                    sessionId = TrainingSessionId("ts-1"),
                    liftId = LiftId("lift-1"),
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    body = "unsynced local note",
                    position = 0,
                    updatedAtEpochMillis = 100L,
                    pendingSync = SyncFlags.PENDING_UPSERT,
                ),
            ),
        )
        val handler = registry.get(SyncCollection.LiftSessionNotes)

        assertTrue(
            handler.upsertFromRemote(
                "ts-1::wl-1",
                mapOf(
                    "session_id" to "ts-1",
                    "lift_id" to "lift-1",
                    "workout_lift_id" to "wl-1",
                    "body" to "stale legacy write",
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                    FirestoreSyncMetadata.DEVICE_ID to "other-device",
                ),
            ),
        )

        assertEquals(
            listOf("unsynced local note"),
            dao.getNotesForSlot(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1")).map { it.body },
        )
        val tombstoned =
            database.pendingCloudDeleteDao().peekPending()
                .filter { it.collectionPath == SyncCollection.LiftSessionNotes.firestorePath }
                .map { it.entityId }
                .toSet()
        assertEquals(setOf("ts-1::wl-1"), tombstoned)
    }

    @Test
    @DisplayName("clean newer sibling is preserved and the losing legacy doc is tombstoned")
    fun cleanNewerSiblingPreserved() = runTest {
        seedSession("ts-1")
        val dao = database.liftSessionNoteDao()
        dao.upsertAll(
            listOf(
                LiftSessionNoteEntity(
                    id = LiftSessionNoteId("uuid-synced"),
                    sessionId = TrainingSessionId("ts-1"),
                    liftId = LiftId("lift-1"),
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    body = "synced newer note",
                    position = 0,
                    updatedAtEpochMillis = 100L,
                    pendingSync = SyncFlags.CLEAN,
                ),
            ),
        )
        val handler = registry.get(SyncCollection.LiftSessionNotes)

        assertTrue(
            handler.upsertFromRemote(
                "ts-1::wl-1",
                mapOf(
                    "session_id" to "ts-1",
                    "lift_id" to "lift-1",
                    "workout_lift_id" to "wl-1",
                    "body" to "older legacy write",
                    FirestoreSyncMetadata.UPDATED_AT to 50L,
                    FirestoreSyncMetadata.DEVICE_ID to "other-device",
                ),
            ),
        )

        assertEquals(
            listOf("synced newer note"),
            dao.getNotesForSlot(TrainingSessionId("ts-1"), WorkoutLiftId("wl-1")).map { it.body },
        )
        val tombstoned =
            database.pendingCloudDeleteDao().peekPending()
                .filter { it.collectionPath == SyncCollection.LiftSessionNotes.firestorePath }
                .map { it.entityId }
                .toSet()
        assertEquals(setOf("ts-1::wl-1"), tombstoned)
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
