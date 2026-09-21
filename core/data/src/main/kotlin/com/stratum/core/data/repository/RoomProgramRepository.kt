package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.LiftSetDao
import com.stratum.core.data.db.ProgramChangeEventDao
import com.stratum.core.data.db.ProgramDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingSessionDao
import com.stratum.core.data.db.WorkoutDao
import com.stratum.core.data.db.WorkoutLiftDao
import com.stratum.core.data.db.WorkoutLiftNoteEntity
import com.stratum.core.data.mapper.rejoinProgram
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.WorkoutLiftNote
import com.stratum.domain.lift.WorkoutLiftNoteInput
import com.stratum.domain.lift.sanitizeLiftNoteBody
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * Room-backed [ProgramRepository]. Multi-program: upserts hierarchy per program id; never deletes
 * sibling programs on save/activate. Child rows for a program are replaced by deleting that
 * program's workouts (FK cascade clears lifts/sets) then re-inserting.
 *
 * Session soft refs are **not** Room FKs, so template rewrite does not null `workout_id` on
 * existing sessions. [deleteProgram] clears soft refs explicitly then removes the template;
 * sessions and logged_sets remain.
 */
class RoomProgramRepository @Inject constructor(
    private val database: StratumDatabase,
    private val programDao: ProgramDao,
    private val workoutDao: WorkoutDao,
    private val workoutLiftDao: WorkoutLiftDao,
    private val liftSetDao: LiftSetDao,
    private val trainingSessionDao: TrainingSessionDao,
    private val programChangeEventDao: ProgramChangeEventDao,
    private val syncTouch: SyncTouch,
) : ProgramRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProgram(): Flow<Program?> =
        // Re-read hierarchy when workout_lifts change (e.g. merge re-point) even if the
        // parent program row is unchanged — observeActive alone would miss those writes.
        combine(
            programDao.observeActive(),
            workoutLiftDao.observeAll(),
        ) { entity, _ -> entity }
            .mapLatest { entity ->
                if (entity == null) null else readProgram(entity.id)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProgramById(programId: ProgramId): Flow<Program?> =
        combine(
            programDao.observeById(programId),
            workoutLiftDao.observeAll(),
        ) { entity, _ -> entity }
            .mapLatest { entity ->
                if (entity == null) null else readProgram(entity.id)
            }

    override fun observeLibraryPrograms(): Flow<List<Program>> =
        programDao.observeLibrary().map { entities ->
            entities.mapNotNull { entity -> readProgram(entity.id) }
        }

    override suspend fun getProgram(): Program? {
        val active = programDao.getActive() ?: return null
        return readProgram(active.id)
    }

    override suspend fun getProgramById(programId: ProgramId): Program? = readProgram(programId)

    override suspend fun getAllUserPrograms(): List<Program> =
        programDao.getAll()
            .filterNot { entity -> entity.id.isImportHistory }
            .mapNotNull { entity -> readProgram(entity.id) }

    override suspend fun saveProgram(program: Program) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                if (program.isActive) {
                    programDao.clearActiveFlags(updatedAtEpochMillis = now)
                }
                upsertHierarchy(program, now)
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun saveToLibrary(program: Program) {
        saveProgram(program.copy(isActive = false))
    }

    override suspend fun activateProgram(programId: ProgramId) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                val existing = programDao.getById(programId)
                    ?: error("Cannot activate unknown program: ${programId.value}")
                check(!existing.id.isImportHistory) {
                    "Import-history program cannot be activated"
                }
                programDao.clearActiveFlags(updatedAtEpochMillis = now)
                programDao.setActive(programId, isActive = true, updatedAtEpochMillis = now)
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun deleteProgram(programId: ProgramId) {
        check(!programId.isImportHistory) {
            "Import-history program cannot be deleted"
        }
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                enqueueHierarchyDeletes(programId, now)
                trainingSessionDao.clearSoftRefsForProgram(programId, updatedAtEpochMillis = now)
                programDao.deleteById(programId)
                syncTouch.enqueueDelete(SyncCollection.Programs, programId.value, now)
            }
        }
    }

    override suspend fun setWorkoutLiftPinnedNote(id: WorkoutLiftId, note: String?): Boolean {
        // Scalar writes go through the list path so the note table and the legacy
        // mirror stay consistent (reads prefer the table). Preserves the first-row
        // id; a blank note clears the list. Missing slots still report false.
        if (workoutLiftDao.getById(id) == null) return false
        val clean = sanitizeLiftNoteBody(note)
        if (clean == null) {
            return setWorkoutLiftPinnedNotes(id, emptyList())
        }
        val existingId = database.workoutLiftNoteDao().idsForWorkoutLift(id).firstOrNull()
        return setWorkoutLiftPinnedNotes(id, listOf(WorkoutLiftNoteInput(id = existingId, body = clean)))
    }

    override suspend fun getWorkoutLiftPinnedNotes(id: WorkoutLiftId): List<WorkoutLiftNote> {
        val rows = database.workoutLiftNoteDao().getForWorkoutLift(id)
        if (rows.isNotEmpty()) return rows.map { it.toWorkoutLiftNote() }
        // Client lazy-migrate for slots that predate the v5 backfill (or were
        // written remotely by an old client): persist the legacy column as the
        // position-0 row so later reads and sync converge on the new shape.
        if (migrateLegacyWorkoutPin(id)) {
            return database.workoutLiftNoteDao().getForWorkoutLift(id).map { it.toWorkoutLiftNote() }
        }
        return emptyList()
    }

    /**
     * Returns true when a legacy-only pin was persisted as the position-0 row.
     * The empty-child check, parent lookup, authoritative pin read, and insert
     * all happen inside one write-locked transaction: a concurrent clear or
     * remote update that commits first leaves nothing eligible, so a stale
     * pre-lock capture can never overwrite its newer value. A second
     * concurrent migrate sees the first one's row and skips (no id churn, no
     * phantom tombstone), and the in-lock parent check keeps a mid-migrate
     * delete from violating the note-table FK out of a read path.
     */
    private suspend fun migrateLegacyWorkoutPin(id: WorkoutLiftId): Boolean {
        val now = System.currentTimeMillis()
        var migrated = false
        syncTouch.withWriteLock {
            database.withTransaction {
                if (database.workoutLiftNoteDao().getForWorkoutLift(id).isEmpty()) {
                    val current =
                        workoutLiftDao.getById(id)?.pinnedNote?.takeIf { it.isNotBlank() }
                    if (current != null) {
                        replaceWorkoutLiftNotes(id, listOf(WorkoutLiftNoteInput(body = current)), now)
                        migrated = true
                    }
                }
            }
            if (migrated) syncTouch.markDirty()
        }
        return migrated
    }

    override suspend fun setWorkoutLiftPinnedNotes(
        id: WorkoutLiftId,
        notes: List<WorkoutLiftNoteInput>,
    ): Boolean {
        if (workoutLiftDao.getById(id) == null) return false
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                replaceWorkoutLiftNotes(id, notes, now)
            }
            syncTouch.markDirty()
        }
        return true
    }

    /**
     * Whole-slot note replace inside the caller's transaction: sanitize, contiguous
     * positions, slot-scoped id retention, plus the legacy first-note mirror.
     * Delete propagation for the new rows lands in PR3.
     */
    private suspend fun replaceWorkoutLiftNotes(
        id: WorkoutLiftId,
        notes: List<WorkoutLiftNoteInput>,
        now: Long,
    ) {
        val noteDao = database.workoutLiftNoteDao()
        val currentIds = noteDao.idsForWorkoutLift(id).toSet()
        val minted =
            notes.mapNotNull { input ->
                sanitizeLiftNoteBody(input.body)?.let { body -> input to body }
            }.mapIndexed { index, (input, body) ->
                WorkoutLiftNoteEntity(
                    id = input.id?.takeIf { it in currentIds }
                        ?: WorkoutLiftNoteId(UUID.randomUUID().toString()),
                    workoutLiftId = id,
                    body = body,
                    position = index,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    pendingSync = SyncFlags.PENDING_UPSERT,
                )
            }
        noteDao.deleteForWorkoutLift(id)
        if (minted.isNotEmpty()) {
            noteDao.upsertAll(minted)
        }
        val retained = minted.map { it.id }.toSet()
        val removed = currentIds.filterNot { it in retained }.map { it.value }
        if (removed.isNotEmpty()) {
            syncTouch.enqueueDelete(SyncCollection.WorkoutLiftNotes, removed, now)
        }
        workoutLiftDao.setPinnedNote(
            id = id,
            note = minted.firstOrNull()?.body,
            updatedAtEpochMillis = now,
        )
    }

    override suspend fun updateCyclePointer(
        programId: ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
        cycleStartEpochDay: Long?,
    ) {
        syncTouch.withWriteLock {
            programDao.updateCyclePointer(
                programId = programId,
                currentCycleNumber = currentCycleNumber,
                cyclePosition = cyclePosition,
                cycleStartEpochDay = cycleStartEpochDay,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            syncTouch.markDirty()
        }
    }

    private suspend fun upsertHierarchy(program: Program, now: Long) {
        val existingSetIds = liftSetDao.idsForProgram(program.id)
        val existingLiftIds = workoutLiftDao.idsForProgram(program.id)
        val existingWorkoutIds = workoutDao.idsForProgram(program.id)
        val nextWorkoutIds = program.workouts.map { it.id.value }
        val nextLiftIds = program.workouts.flatMap { w -> w.workoutLifts.map { it.id.value } }
        val nextSetIds =
            program.workouts
                .flatMap { w -> w.workoutLifts.flatMap { l -> l.sets.map { it.id.value } } }

        syncTouch.replaceIds(SyncCollection.LiftSets, existingSetIds, nextSetIds, now)
        syncTouch.replaceIds(SyncCollection.WorkoutLifts, existingLiftIds, nextLiftIds, now)
        syncTouch.replaceIds(SyncCollection.Workouts, existingWorkoutIds, nextWorkoutIds, now)
        syncTouch.cancelDelete(SyncCollection.Programs, program.id.value)

        // Snapshot per-slot pins + note rows: the delete below CASCADE-clears both, and
        // the reinsert must restore lists for slots whose scalar pin is untouched while
        // dropping stale rows where the save changed the pin (scalar authoritative).
        val prevPins =
            workoutDao.getForProgram(program.id.value)
                .flatMap { workout -> workoutLiftDao.getForWorkout(workout.id.value) }
                .associate { it.id to it.pinnedNote }
        val prevNotes =
            if (prevPins.isEmpty()) {
                emptyList()
            } else {
                database.workoutLiftNoteDao().getForWorkoutLifts(prevPins.keys.toList())
            }
        workoutDao.deleteForProgram(program.id)
        programDao.upsert(program.toEntity(nowMillis = now))
        if (program.workouts.isEmpty()) return
        workoutDao.upsertAll(program.workouts.map { it.toEntity(nowMillis = now) })
        val lifts = program.workouts.flatMap { it.workoutLifts }
        if (lifts.isEmpty()) return
        workoutLiftDao.upsertAll(lifts.map { it.toEntity(nowMillis = now) })
        val nextPins = lifts.associate { it.id to it.pinnedNote }
        // Slots carrying an explicit ordered list through the template save
        // replace their stored rows; everything else keeps the
        // scalar-authoritative restore behavior.
        val carriedNoteLists = lifts
            .filter { it.pinnedNotes.isNotEmpty() }
            .associate { it.id to it.pinnedNotes }
        val restored =
            prevNotes.filter { row ->
                row.workoutLiftId in nextPins &&
                    row.workoutLiftId !in carriedNoteLists &&
                    prevPins[row.workoutLiftId] == nextPins[row.workoutLiftId]
            }
        if (restored.isNotEmpty()) {
            database.workoutLiftNoteDao().upsertAll(restored)
        }
        for ((slotId, bodies) in carriedNoteLists) {
            replaceWorkoutLiftNotes(
                slotId,
                bodies.map { body -> WorkoutLiftNoteInput(body = body) },
                now,
            )
            // The hierarchy delete CASCADE-cleared the previous rows without
            // tombstones; enqueue deletes so synced devices drop replaced rows.
            val replacedIds = prevNotes.filter { it.workoutLiftId == slotId }.map { it.id.value }
            if (replacedIds.isNotEmpty()) {
                syncTouch.enqueueDelete(SyncCollection.WorkoutLiftNotes, replacedIds, now)
            }
        }
        // Tombstone note rows of removed slots before the CASCADE delete clears them.
        val removedNoteIds =
            prevNotes
                .filterNot { it.workoutLiftId in nextPins }
                .map { it.id.value }
        if (removedNoteIds.isNotEmpty()) {
            syncTouch.enqueueDelete(SyncCollection.WorkoutLiftNotes, removedNoteIds, now)
        }
        val sets = lifts.flatMap { it.sets }
        if (sets.isNotEmpty()) {
            liftSetDao.upsertAll(sets.map { it.toEntity(nowMillis = now) })
        }
    }

    private suspend fun enqueueHierarchyDeletes(programId: ProgramId, now: Long) {
        syncTouch.enqueueDelete(SyncCollection.LiftSets, liftSetDao.idsForProgram(programId), now)
        syncTouch.enqueueDelete(SyncCollection.WorkoutLifts, workoutLiftDao.idsForProgram(programId), now)
        syncTouch.enqueueDelete(
            SyncCollection.WorkoutLiftNotes,
            database.workoutLiftNoteDao().idsForProgram(programId).map { it.value },
            now,
        )
        syncTouch.enqueueDelete(SyncCollection.Workouts, workoutDao.idsForProgram(programId), now)
        // Events CASCADE-delete locally; enqueue tombstones so remote devices drop orphans.
        syncTouch.enqueueDelete(
            SyncCollection.ProgramChangeEvents,
            programChangeEventDao.idsForProgram(programId),
            now,
        )
    }

    private suspend fun readProgram(programId: ProgramId): Program? =
        database.withTransaction {
            val programEntity = programDao.getById(programId) ?: return@withTransaction null
            val workouts = workoutDao.getForProgram(programEntity.id.value)
            val liftsByWorkout =
                workouts.associate { workout ->
                    workout.id.value to workoutLiftDao.getForWorkout(workout.id.value)
                }
            val setsByLift =
                liftsByWorkout.values
                    .flatten()
                    .associate { lift ->
                        lift.id.value to liftSetDao.getForWorkoutLift(lift.id.value)
                    }
            rejoinProgram(programEntity, workouts, liftsByWorkout, setsByLift)
        }
}

internal fun WorkoutLiftNoteEntity.toWorkoutLiftNote(): WorkoutLiftNote =
    WorkoutLiftNote(
        id = id,
        workoutLiftId = workoutLiftId,
        body = body,
        position = position,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
