package com.stratum.core.data.subscription

import kotlinx.coroutines.flow.Flow

interface ComplimentaryAccessStore {
    fun observeComplimentaryAccess(userId: String): Flow<Boolean>

    suspend fun getComplimentaryAccess(userId: String): Boolean
}
