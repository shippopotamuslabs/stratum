package com.stratum.core.infrastructure.repository

import com.stratum.core.infrastructure.db.UserProfileDao
import com.stratum.core.infrastructure.mapper.toDomain
import com.stratum.core.infrastructure.mapper.toEntity
import com.stratum.core.infrastructure.sync.SyncCollection
import com.stratum.core.infrastructure.sync.SyncTouch
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.application.onboarding.UserTrainingProfileRepository
import com.stratum.domain.onboarding.identity.UserProfileId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUserTrainingProfileRepository @Inject constructor(
    private val dao: UserProfileDao,
    private val syncTouch: SyncTouch,
) : UserTrainingProfileRepository {
    override fun observeProfile(): Flow<UserTrainingProfile?> =
        dao.observe().map { it?.toDomain() }

    override suspend fun saveProfile(profile: UserTrainingProfile) {
        syncTouch.mutate {
            dao.upsert(profile.toEntity())
            syncTouch.cancelDelete(SyncCollection.UserProfile, UserProfileId.Default.value)
        }
    }
}
