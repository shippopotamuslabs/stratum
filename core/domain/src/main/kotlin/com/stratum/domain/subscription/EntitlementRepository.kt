package com.stratum.domain.subscription

import kotlinx.coroutines.flow.Flow

interface EntitlementRepository {
    fun observeEntitlement(): Flow<EntitlementState>

    suspend fun refreshEntitlement(): Result<EntitlementState>
}
