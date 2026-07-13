package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.LiftSetDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingLoggedSetDao
import com.stratum.core.data.db.WorkoutLiftDao
import com.stratum.core.data.db.WorkoutLiftEntity
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.MergeLiftsPort
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-transaction merge: reassign logged sets, re-point workout_lifts (collapsing duplicate
 * slots in a workout), hide seed sources, delete custom sources (+ cloud tombstones).
 */
@Singleton
class RoomMergeLiftsPort @Inject constructor(
    private val database: StratumDatabase,
    private val loggedSetDao: TrainingLoggedSetDao,
    private val workoutLiftDao: WorkoutLiftDao,
    private val liftSetDao: LiftSetDao,
    private val liftDao: LiftDao,
    private val syncTouch: SyncTouch,
) : MergeLiftsPort {

    override suspend fun applyMerge(
        targetLiftId: LiftId,
        targetName: String,
        sourcesToHide: Set<LiftId>,
        sourcesToDelete: Set<LiftId>,
    ) {
        val allSources = sourcesToHide + sourcesToDelete
        require(allSources.isNotEmpty()) { "At least one source lift is required" }
        require(targetLiftId !in allSources) { "Target lift cannot also be a source" }

        val sourceList = allSources.toList()
        val sourceSet = allSources
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

                collapseAndRepointWorkoutLifts(
                    sourceLiftIds = sourceSet,
                    targetLiftId = targetLiftId,
                    now = now,
                )

                if (sourcesToHide.isNotEmpty()) {
                    liftDao.setEnabledForIds(
                        ids = sourcesToHide.toList(),
                        enabled = false,
                        updatedAtEpochMillis = now,
                    )
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

    /**
     * Avoids two template slots for the same lift in one workout:
     * - Workout already has the target → drop source slots (keep original target scheme/sets).
     * - Multiple sources, no target yet → keep lowest-position source, drop the rest, then re-point.
     * - Single source slot → re-point.
     */
    private suspend fun collapseAndRepointWorkoutLifts(
        sourceLiftIds: Set<LiftId>,
        targetLiftId: LiftId,
        now: Long,
    ) {
        val allSlots = workoutLiftDao.getAll()
        val drop = mutableListOf<WorkoutLiftEntity>()

        for ((_, slots) in allSlots.groupBy { it.workoutId }) {
            val hasTarget = slots.any { it.liftId == targetLiftId }
            val sourceSlots = slots.filter { it.liftId in sourceLiftIds }.sortedBy { it.position }
            if (sourceSlots.isEmpty()) continue

            if (hasTarget) {
                drop += sourceSlots
            } else if (sourceSlots.size > 1) {
                drop += sourceSlots.drop(1)
            }
        }

        deleteWorkoutLiftsWithSync(drop, now)

        val remainingSourceIds =
            workoutLiftDao.getAll()
                .map { it.liftId }
                .filter { it in sourceLiftIds }
                .distinct()
        if (remainingSourceIds.isNotEmpty()) {
            workoutLiftDao.repointLiftIds(
                sourceLiftIds = remainingSourceIds,
                targetLiftId = targetLiftId,
                updatedAtEpochMillis = now,
            )
        }

        collapseDuplicateTargetSlots(targetLiftId, now)
    }

    private suspend fun collapseDuplicateTargetSlots(targetLiftId: LiftId, now: Long) {
        val duplicates =
            workoutLiftDao.getAll()
                .filter { it.liftId == targetLiftId }
                .groupBy { it.workoutId }
                .values
                .flatMap { slots ->
                    if (slots.size <= 1) {
                        emptyList()
                    } else {
                        slots.sortedBy { it.position }.drop(1)
                    }
                }
        deleteWorkoutLiftsWithSync(duplicates, now)
    }

    private suspend fun deleteWorkoutLiftsWithSync(
        slots: List<WorkoutLiftEntity>,
        now: Long,
    ) {
        if (slots.isEmpty()) return
        val setIds = mutableListOf<String>()
        for (slot in slots) {
            setIds += liftSetDao.idsForWorkoutLift(slot.id.value)
        }
        if (setIds.isNotEmpty()) {
            syncTouch.enqueueDelete(SyncCollection.LiftSets, setIds, now)
        }
        syncTouch.enqueueDelete(
            SyncCollection.WorkoutLifts,
            slots.map { it.id.value },
            now,
        )
        for (slot in slots) {
            workoutLiftDao.deleteById(slot.id)
        }
    }
}
