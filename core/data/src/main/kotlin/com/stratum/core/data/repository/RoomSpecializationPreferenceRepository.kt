package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.db.SpecializationPreferenceDao
import com.stratum.core.infrastructure.mapper.toDomain
import com.stratum.core.infrastructure.mapper.toEntities
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.application.onboarding.SpecializationPreferenceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [SpecializationPreferenceRepository]. [save] replaces the stored
 * preference atomically inside a transaction (clear + insertAll) so no orphaned rows remain
 * when the user edits their selection. Removed rows are enqueued for cloud delete.
 */
class RoomSpecializationPreferenceRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: SpecializationPreferenceDao,
    private val syncTouch: SyncTouch,
) : SpecializationPreferenceRepository {
    override fun observe(): Flow<SpecializationPreference> =
        dao.observeAll().map { it.toDomain() }

    override suspend fun save(preference: SpecializationPreference) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                val next = preference.toEntities(nowMillis = now)
                val nextIds = next.map { it.id.value }
                val previousIds = dao.allIds()
                syncTouch.replaceIds(
                    SyncCollection.SpecializationPreference,
                    previousIds = previousIds,
                    nextIds = nextIds,
                    nowMillis = now,
                )
                dao.clear()
                dao.upsertAll(next)
            }
            syncTouch.markDirty()
        }
    }
}
