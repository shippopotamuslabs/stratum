package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.LiftSessionNoteDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingLoggedSetDao
import com.stratum.core.data.db.WorkoutLiftDao
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.MergeLiftsPort
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-transaction merge: reassign logged sets, re-point all program `workout_lifts` that
 * referenced sources onto the survivor (duplicate catalog lifts in one workout are allowed),
 * tombstone seed sources (STR-131 — merged away means deleted, never merely
 * disabled), delete custom sources (+ cloud tombstones).
 */
@Singleton
class RoomMergeLiftsPort @Inject constructor(
    private val database: StratumDatabase,
    private val loggedSetDao: TrainingLoggedSetDao,
    private val workoutLiftDao: WorkoutLiftDao,
    private val liftDao: LiftDao,
    private val liftSessionNoteDao: LiftSessionNoteDao,
    private val syncTouch: SyncTouch,
) : MergeLiftsPort {

    override suspend fun applyMerge(
        targetLiftId: LiftId,
        targetName: String,
        sourcesToTombstone: Set<LiftId>,
        sourcesToDelete: Set<LiftId>,
    ) {
        val allSources = sourcesToTombstone + sourcesToDelete
        require(allSources.isNotEmpty()) { "At least one source lift is required" }
        require(targetLiftId !in allSources) { "Target lift cannot also be a source" }

        val sourceList = allSources.toList()
        val now = System.currentTimeMillis()

        syncTouch.withWriteLock {
            database.withTransaction {
                loggedSetDao.clearPersonalRecordsForLift(targetLiftId, now)
                loggedSetDao.reassignSetsToLift(
                    sourceLiftIds = sourceList,
                    targetLiftId = targetLiftId,
                    targetName = targetName,
                    updatedAtEpochMillis = now,
                )

                workoutLiftDao.repointLiftIds(
                    sourceLiftIds = sourceList,
                    targetLiftId = targetLiftId,
                    updatedAtEpochMillis = now,
                )

                liftSessionNoteDao.remapLiftIds(
                    sourceLiftIds = sourceList,
                    targetLiftId = targetLiftId,
                    updatedAtEpochMillis = now,
                )

                // Seed sources tombstone after re-pointing (same transaction):
                // their sets and slots already point at the target, so no live
                // pointer references the tombstoned rows. `enabled` is left
                // as-is — it carries no meaning on a tombstoned row. Their
                // pair-scoped stash notes are swept too (STR-131 invariant: a
                // tombstoned lift keeps no live pointers and its stashed notes
                // must not outlive it — same sweep as the Gym delete path;
                // custom sources' notes CASCADE with their hard delete).
                if (sourcesToTombstone.isNotEmpty()) {
                    val noteDao = database.workoutLiftNoteDao()
                    val stashNoteIds =
                        sourcesToTombstone.flatMap { id ->
                            noteDao.getForLiftId(id).map { it.id.value }
                        }
                    for (id in sourcesToTombstone) {
                        noteDao.deleteByLiftId(id)
                        liftDao.tombstoneById(id, now)
                    }
                    if (stashNoteIds.isNotEmpty()) {
                        syncTouch.enqueueDelete(
                            SyncCollection.WorkoutLiftNotes,
                            stashNoteIds,
                            now,
                        )
                    }
                }
                if (sourcesToDelete.isNotEmpty()) {
                    for (id in sourcesToDelete) {
                        liftDao.deleteById(id)
                    }
                    syncTouch.enqueueDelete(
                        SyncCollection.Lifts,
                        sourcesToDelete.map { it.value },
                        now,
                    )
                }
            }
            syncTouch.markDirty()
        }
    }
}
