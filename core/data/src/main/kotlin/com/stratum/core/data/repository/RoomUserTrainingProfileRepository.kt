package com.stratum.core.data.repository

import com.stratum.core.data.db.UserProfileDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUserTrainingProfileRepository @Inject constructor(
    private val dao: UserProfileDao,
) : UserTrainingProfileRepository {
    override fun observeProfile(): Flow<UserTrainingProfile?> =
        dao.observe().map { it?.toDomain() }

    override suspend fun saveProfile(profile: UserTrainingProfile) {
        dao.upsert(profile.toEntity())
    }
}
