package com.stratum.application.subscription

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementState

interface EntitlementRepository {
    fun observeEntitlement(): Flow<EntitlementState>

    suspend fun refreshEntitlement(): Result<EntitlementState>

    /**
     * Fire-and-forget Play sync failures as values (CODE_STYLE_RULES
     * § "Partial success"). Repository implementations never log; the app-shell
     * boundary drains each diagnostic through `LoggingPort` exactly once.
     */
    fun observeEntitlementDiagnostics(): Flow<EntitlementDiagnostic>
}
