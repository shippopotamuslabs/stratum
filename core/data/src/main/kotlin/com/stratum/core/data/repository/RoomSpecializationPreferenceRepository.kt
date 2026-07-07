package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.SpecializationPreferenceDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntities
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.SpecializationPreferenceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [SpecializationPreferenceRepository]. [save] replaces the stored
 * preference atomically inside a transaction (clear + insertAll) so no orphaned rows remain
 * when the user edits their selection.
 */
class RoomSpecializationPreferenceRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: SpecializationPreferenceDao,
) : SpecializationPreferenceRepository {
    override fun observe(): Flow<SpecializationPreference> =
        dao.observeAll().map { it.toDomain() }

    override suspend fun save(preference: SpecializationPreference) {
        database.withTransaction {
            dao.clear()
            dao.upsertAll(preference.toEntities())
        }
    }
}
