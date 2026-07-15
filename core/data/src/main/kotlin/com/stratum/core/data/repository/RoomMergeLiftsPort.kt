package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.LiftDao
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
 * hide seed sources, delete custom sources (+ cloud tombstones).
 */
@Singleton
class RoomMergeLiftsPort @Inject constructor(
    private val database: StratumDatabase,
    private val loggedSetDao: TrainingLoggedSetDao,
    private val workoutLiftDao: WorkoutLiftDao,
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
}
