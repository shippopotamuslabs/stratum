package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_training_profile WHERE id = ${UserProfileEntity.ID}")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_training_profile WHERE id = ${UserProfileEntity.ID}")
    suspend fun getOrNull(): UserProfileEntity?

    @Upsert
    suspend fun upsert(entity: UserProfileEntity)
}
