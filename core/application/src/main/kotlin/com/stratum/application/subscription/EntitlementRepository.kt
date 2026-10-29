package com.stratum.application.subscription

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.subscription.EntitlementState

interface EntitlementRepository {
    fun observeEntitlement(): Flow<EntitlementState>

    suspend fun refreshEntitlement(): Result<EntitlementState>
}
