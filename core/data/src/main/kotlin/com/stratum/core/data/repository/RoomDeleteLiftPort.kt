package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncTouch
import com.stratum.application.lift.DeleteLiftPort
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-transaction lift delete (STR-131): sweep the lift's live template
 * references, then tombstone the row. The tombstone propagates as an
 * `is_deleted` upsert (no sync delete record for the lift row itself); the
 * swept slot / set / note rows get sync delete records so other devices drop
 * them even before the tombstone arrives. Any failure rolls the whole delete
 * back — the lift stays fully live.
 */
@Singleton
class RoomDeleteLiftPort @Inject constructor(
    private val database: StratumDatabase,
    private val liftDao: LiftDao,
    private val syncTouch: SyncTouch,
) : DeleteLiftPort {

    override suspend fun deleteLift(liftId: LiftId): Boolean {
        val existing = liftDao.getById(liftId) ?: return false
        if (existing.isDeleted) return false

        val now = System.currentTimeMillis()
        var tombstoned = false
        syncTouch.withWriteLock {
            database.withTransaction {
                val sweep = sweepLiftTemplateReferences(database, liftId)
                if (sweep.setIds.isNotEmpty()) {
                    syncTouch.enqueueDelete(SyncCollection.LiftSets, sweep.setIds, now)
                }
                if (sweep.slotIds.isNotEmpty()) {
                    syncTouch.enqueueDelete(SyncCollection.WorkoutLifts, sweep.slotIds, now)
                }
                if (sweep.noteIds.isNotEmpty()) {
                    syncTouch.enqueueDelete(SyncCollection.WorkoutLiftNotes, sweep.noteIds, now)
                }
                tombstoned = liftDao.tombstoneById(liftId, now) == 1
            }
            if (tombstoned) syncTouch.markDirty()
        }
        return tombstoned
    }
}
