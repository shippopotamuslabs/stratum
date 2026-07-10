package com.stratum.core.data.sync.appliers

import androidx.room.Room
import com.stratum.core.data.db.LoggedSetEntity
import com.stratum.core.data.db.ProgramEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingSessionEntity
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
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

/**
 * R2 F5: PR demotion must use a non-regressing clock (CASE) like program demotion.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LoggedSetPrDemotionClockTest {
    private lateinit var database: StratumDatabase
    private lateinit var subject: LoggedSetRemoteApplier

    @BeforeEach
    fun setup() = runTest {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        database.programDao().upsert(
            ProgramEntity(
                id = ProgramId("prog-1"),
                name = "Test",
                createdAt = "2025-01-01T00:00:00Z",
                cycleLength = 1,
            ),
        )
        database.trainingSessionDao().upsert(
            TrainingSessionEntity(
                id = TrainingSessionId("session-1"),
                programId = ProgramId("prog-1"),
                workoutId = null,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = 9_000L,
                sourceApp = null,
            ),
        )
        subject = LoggedSetRemoteApplier(database.trainingLoggedSetDao(), SyncEntityDecoders())
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("demotion floor raises older PR clock and marks PENDING")
    fun olderClockRaisedToFloor() = runTest {
        val dao = database.trainingLoggedSetDao()
        dao.upsert(
            localPr(
                id = "local-pr",
                updatedAt = 100L,
            ),
        )

        val ok =
            subject.upsertFromRemote(
                id = "remote-pr",
                data = remotePrPayload(id = "remote-pr", updatedAt = 500L),
            )
        assertTrue(ok)

        val demoted = dao.getById(LoggedSetId("local-pr"))!!
        assertFalse(demoted.isPersonalRecord == true)
        assertEquals(SyncFlags.PENDING_UPSERT, demoted.pendingSync)
        // Floor is max(remote=500, now) — at least 500; wall clock may be higher.
        assertTrue(demoted.updatedAtEpochMillis >= 500L)

        val winner = dao.getById(LoggedSetId("remote-pr"))!!
        assertEquals(true, winner.isPersonalRecord)
        assertEquals(SyncFlags.CLEAN, winner.pendingSync)
    }

    @Test
    @DisplayName("demotion does not rewind a newer local PR clock")
    fun newerClockNotRegressed() = runTest {
        val dao = database.trainingLoggedSetDao()
        val newerLocalClock = System.currentTimeMillis() + 60_000L
        dao.upsert(
            localPr(
                id = "local-pr",
                updatedAt = newerLocalClock,
            ),
        )

        val ok =
            subject.upsertFromRemote(
                id = "remote-pr",
                data = remotePrPayload(id = "remote-pr", updatedAt = 500L),
            )
        assertTrue(ok)

        val demoted = dao.getById(LoggedSetId("local-pr"))!!
        assertFalse(demoted.isPersonalRecord == true)
        assertEquals(SyncFlags.PENDING_UPSERT, demoted.pendingSync)
        // Non-regressing CASE keeps the higher local clock.
        assertEquals(newerLocalClock, demoted.updatedAtEpochMillis)
    }

    private fun localPr(id: String, updatedAt: Long): LoggedSetEntity =
        LoggedSetEntity(
            id = LoggedSetId(id),
            sessionId = TrainingSessionId("session-1"),
            exerciseNameRaw = "Bench Press",
            liftId = LiftId("bench-press"),
            setIndex = 0,
            weightAmount = 135.0,
            weightUnit = "Lb",
            reps = 5,
            rir = 2,
            completedAtEpochMillis = 2_000L,
            isPersonalRecord = true,
            updatedAtEpochMillis = updatedAt,
            pendingSync = SyncFlags.CLEAN,
        )

    private fun remotePrPayload(id: String, updatedAt: Long): Map<String, Any?> =
        mapOf(
            "session_id" to "session-1",
            "exercise_name_raw" to "Bench Press",
            "lift_id" to "bench-press",
            "set_index" to 1,
            "weight_amount" to 145.0,
            "weight_unit" to "Lb",
            "reps" to 5,
            "completed_at_epoch_millis" to 3_000L,
            "is_personal_record" to true,
            FirestoreSyncMetadata.UPDATED_AT to updatedAt,
        )
}
