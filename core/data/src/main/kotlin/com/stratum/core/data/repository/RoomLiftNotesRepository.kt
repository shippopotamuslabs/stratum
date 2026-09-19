package com.stratum.core.data.repository

import com.stratum.core.data.db.LiftSessionNoteDao
import com.stratum.core.data.db.LiftSessionNoteEntity
import com.stratum.core.data.db.LiftSessionNoteWithSession
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.LiftNotesRepository
import com.stratum.domain.lift.LiftSessionNote
import com.stratum.domain.lift.PastSessionLiftNote
import com.stratum.domain.lift.SessionNoteInput
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.lift.sanitizeLiftNoteBody
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLiftNotesRepository @Inject constructor(
    private val dao: LiftSessionNoteDao,
    private val syncTouch: SyncTouch,
) : LiftNotesRepository {
    override suspend fun getSessionNote(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): LiftSessionNote? = dao.getBySessionAndWorkoutLift(sessionId, workoutLiftId)?.toDomain()

    override suspend fun upsertSessionNote(note: LiftSessionNote) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            val displaced = dao.upsertMergingUniqueSlot(note.toEntity(nowMillis = now))
            if (displaced != null) {
                syncTouch.enqueueDelete(SyncCollection.LiftSessionNotes, displaced, now)
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun getSessionNotes(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): List<LiftSessionNote> =
        dao.getBySessionAndWorkoutLift(sessionId, workoutLiftId)?.toDomain()?.let(::listOf)
            .orEmpty()

    override suspend fun replaceSessionNotes(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
        liftId: LiftId,
        notes: List<SessionNoteInput>,
    ) {
        // PR1 legacy-backed shim: the single-row slot keeps the first surviving note
        // (PR2 relaxes the unique slot to multi-row). No production caller sends lists yet.
        val first = notes.mapNotNull { sanitizeLiftNoteBody(it.body) }.firstOrNull()
        if (first == null) {
            deleteSessionNote(sessionId, workoutLiftId)
            return
        }
        val existing = dao.getBySessionAndWorkoutLift(sessionId, workoutLiftId)
        upsertSessionNote(
            LiftSessionNote(
                id = existing?.id ?: LiftSessionNoteId.forSlot(sessionId, workoutLiftId),
                sessionId = sessionId,
                liftId = liftId,
                workoutLiftId = workoutLiftId,
                body = first,
                updatedAt = Instant.now(),
            ),
        )
    }

    override suspend fun deleteSessionNote(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ) {
        val existing = dao.getBySessionAndWorkoutLift(sessionId, workoutLiftId) ?: return
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            dao.deleteBySessionAndWorkoutLift(sessionId, workoutLiftId)
            syncTouch.enqueueDelete(SyncCollection.LiftSessionNotes, existing.id.value, now)
        }
    }

    override suspend fun getPastNotesForLift(
        liftId: LiftId,
        excludeSessionId: TrainingSessionId?,
    ): List<PastSessionLiftNote> {
        val rows =
            if (excludeSessionId == null) {
                dao.getPastNotesForLift(liftId)
            } else {
                dao.getPastNotesForLiftExcluding(liftId, excludeSessionId)
            }
        return rows.map { it.toPastNote() }
    }

    override suspend fun remapLiftIds(sourceLiftIds: Set<LiftId>, targetLiftId: LiftId) {
        if (sourceLiftIds.isEmpty()) return
        syncTouch.withWriteLock {
            dao.remapLiftIds(
                sourceLiftIds = sourceLiftIds.toList(),
                targetLiftId = targetLiftId,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            syncTouch.markDirty()
        }
    }
}

internal fun LiftSessionNoteEntity.toDomain(): LiftSessionNote =
    LiftSessionNote(
        id = id,
        sessionId = sessionId,
        liftId = liftId,
        workoutLiftId = workoutLiftId,
        body = body,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )

internal fun LiftSessionNote.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): LiftSessionNoteEntity =
    LiftSessionNoteEntity(
        id = id,
        sessionId = sessionId,
        liftId = liftId,
        workoutLiftId = workoutLiftId,
        body = body,
        updatedAtEpochMillis = nowMillis,
        pendingSync = SyncFlags.PENDING_UPSERT,
    )

internal fun LiftSessionNoteWithSession.toPastNote(): PastSessionLiftNote =
    PastSessionLiftNote(
        sessionId = note.sessionId,
        workoutLiftId = note.workoutLiftId,
        body = note.body,
        sessionStartedAt = Instant.ofEpochMilli(startedAtEpochMillis),
        workoutName = workoutNameSnapshot,
    )
