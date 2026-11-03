package com.stratum.core.infrastructure.subscription

import com.stratum.domain.subscription.ComplimentaryAccess
import kotlinx.coroutines.flow.Flow

interface ComplimentaryAccessStore {
    fun observeComplimentaryAccess(userId: String): Flow<ComplimentaryAccess>

    suspend fun getComplimentaryAccess(userId: String): ComplimentaryAccess
}
