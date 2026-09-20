package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.LiftSetDao
import com.stratum.core.data.db.ProgramChangeEventDao
import com.stratum.core.data.db.ProgramDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingSessionDao
import com.stratum.core.data.db.WorkoutDao
import com.stratum.core.data.db.WorkoutLiftDao
import com.stratum.core.data.mapper.rejoinProgram
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.sync.SyncCollection
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
        var updated = 0
        syncTouch.withWriteLock {
            updated =
                workoutLiftDao.setPinnedNote(
                    id = id,
                    note = note,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            if (updated > 0) syncTouch.markDirty()
        }
        return updated > 0
    }

    override suspend fun getWorkoutLiftPinnedNotes(id: WorkoutLiftId): List<WorkoutLiftNote> {
        val row = workoutLiftDao.getById(id) ?: return emptyList()
        val body = row.pinnedNote?.takeIf { it.isNotBlank() } ?: return emptyList()
        return listOf(
            WorkoutLiftNote(
                id = WorkoutLiftNoteId("${id.value}::0"),
                workoutLiftId = id,
                body = body,
                position = 0,
                updatedAt = Instant.ofEpochMilli(row.updatedAtEpochMillis),
            ),
        )
    }

    override suspend fun setWorkoutLiftPinnedNotes(
        id: WorkoutLiftId,
        notes: List<WorkoutLiftNoteInput>,
    ): Boolean {
        // PR1 legacy-backed shim: the single pinned_note column keeps the first surviving
        // note (PR2 moves list storage to workout_lift_notes). No production caller sends
        // lists yet — the use-case list path is covered by fakes until then.
        val first = notes.mapNotNull { sanitizeLiftNoteBody(it.body) }.firstOrNull()
        return setWorkoutLiftPinnedNote(id, first)
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

        workoutDao.deleteForProgram(program.id)
        programDao.upsert(program.toEntity(nowMillis = now))
        if (program.workouts.isEmpty()) return
        workoutDao.upsertAll(program.workouts.map { it.toEntity(nowMillis = now) })
        val lifts = program.workouts.flatMap { it.workoutLifts }
        if (lifts.isEmpty()) return
        workoutLiftDao.upsertAll(lifts.map { it.toEntity(nowMillis = now) })
        val sets = lifts.flatMap { it.sets }
        if (sets.isNotEmpty()) {
            liftSetDao.upsertAll(sets.map { it.toEntity(nowMillis = now) })
        }
    }

    private suspend fun enqueueHierarchyDeletes(programId: ProgramId, now: Long) {
        syncTouch.enqueueDelete(SyncCollection.LiftSets, liftSetDao.idsForProgram(programId), now)
        syncTouch.enqueueDelete(SyncCollection.WorkoutLifts, workoutLiftDao.idsForProgram(programId), now)
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
