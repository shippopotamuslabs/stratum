package com.stratum.core.infrastructure.repository

import androidx.room.withTransaction
import com.stratum.core.infrastructure.db.LiftSessionNoteDao
import com.stratum.core.infrastructure.db.LiftSessionNoteEntity
import com.stratum.core.infrastructure.db.LiftSessionNoteWithSession
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.sync.SyncCollection
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.core.infrastructure.sync.SyncTouch
import com.stratum.application.lift.LiftNotesRepository
import com.stratum.domain.lift.LiftSessionNote
import com.stratum.domain.lift.PastSessionLiftNote
import com.stratum.domain.lift.SessionNoteInput
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.lift.sanitizeLiftNoteBody
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLiftNotesRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: LiftSessionNoteDao,
    private val syncTouch: SyncTouch,
) : LiftNotesRepository {
    override suspend fun getSessionNote(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): LiftSessionNote? = getSessionNotes(sessionId, workoutLiftId).firstOrNull()

    /**
     * Surgical single-row write: updates or inserts only this note's row, retaining
     * sibling rows in the slot. Whole-slot replacement (with tombstones) is reserved
     * for [replaceSessionNotes], where the caller supplies the full list.
     */
    override suspend fun upsertSessionNote(note: LiftSessionNote) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            val existing = dao.getById(note.id)
            dao.upsert(
                LiftSessionNoteEntity(
                    id = note.id,
                    sessionId = note.sessionId,
                    liftId = note.liftId,
                    workoutLiftId = note.workoutLiftId,
                    body = note.body,
                    position = existing?.position ?: note.position,
                    createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                    updatedAtEpochMillis = now,
                    pendingSync = SyncFlags.PENDING_UPSERT,
                ),
            )
            syncTouch.markDirty()
        }
    }

    override suspend fun getSessionNotes(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): List<LiftSessionNote> = dao.getNotesForSlot(sessionId, workoutLiftId).map { it.toDomain() }

    override suspend fun replaceSessionNotes(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
        liftId: LiftId,
        notes: List<SessionNoteInput>,
    ) {
        replaceSlot(
            sessionId = sessionId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            inputs = notes,
        )
    }

    /**
     * Whole-slot replace: sanitize (drop blanks, truncate), contiguous positions, slot-scoped
     * id retention (unknown ids mint new rows). Removed rows get cloud tombstones so remote
     * devices drop them; an empty surviving list clears the slot.
     */
    private suspend fun replaceSlot(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
        liftId: LiftId,
        inputs: List<SessionNoteInput>,
    ) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                val currentIds = dao.getIdsForSlot(sessionId, workoutLiftId).toSet()
                val minted =
                    inputs.mapNotNull { input ->
                        sanitizeLiftNoteBody(input.body)?.let { body -> input to body }
                    }.mapIndexed { index, (input, body) ->
                        LiftSessionNoteEntity(
                            id = input.id?.takeIf { it in currentIds }
                                ?: LiftSessionNoteId(UUID.randomUUID().toString()),
                            sessionId = sessionId,
                            liftId = liftId,
                            workoutLiftId = workoutLiftId,
                            body = body,
                            position = index,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                            pendingSync = SyncFlags.PENDING_UPSERT,
                        )
                    }
                dao.deleteBySessionAndWorkoutLift(sessionId, workoutLiftId)
                if (minted.isNotEmpty()) {
                    dao.upsertAll(minted)
                }
                val retained = minted.map { it.id }.toSet()
                val removed = currentIds.filterNot { it in retained }.map { it.value }
                if (removed.isNotEmpty()) {
                    syncTouch.enqueueDelete(SyncCollection.LiftSessionNotes, removed, now)
                }
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun deleteSessionNote(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ) {
        val existing = dao.getIdsForSlot(sessionId, workoutLiftId)
        if (existing.isEmpty()) return
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            dao.deleteBySessionAndWorkoutLift(sessionId, workoutLiftId)
            syncTouch.enqueueDelete(
                SyncCollection.LiftSessionNotes,
                existing.map { it.value },
                now,
            )
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
        // Kotlin blank semantics (all Unicode whitespace), matching
        // sanitizeLiftNoteBody — SQLite TRIM in the queries only strips spaces,
        // so tab/newline-only bodies retained by the v5 backfill are dropped here.
        return rows.map { it.toPastNote() }.filter { it.body.isNotBlank() }
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
        position = position,
    )

internal fun LiftSessionNoteWithSession.toPastNote(): PastSessionLiftNote =
    PastSessionLiftNote(
        sessionId = note.sessionId,
        workoutLiftId = note.workoutLiftId,
        body = note.body,
        sessionStartedAt = Instant.ofEpochMilli(startedAtEpochMillis),
        workoutName = workoutNameSnapshot,
    )
