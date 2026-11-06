package com.stratum.application.subscription

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementState

/** Outcome of re-reading entitlement at the [EntitlementRepository]. */
sealed interface RefreshEntitlementOutcome {
    data class Success(val state: EntitlementState) : RefreshEntitlementOutcome

    data class Failed(
        val diagnostics: List<BillingDiagnostic>,
    ) : RefreshEntitlementOutcome
}

interface EntitlementRepository {
    fun observeEntitlement(): Flow<EntitlementState>

    /**
     * Re-reads entitlement from its sources; an operational failure is the
     * [RefreshEntitlementOutcome.Failed] branch (STR-243 remediation) —
     * no exception crosses this port in place of an outcome (coroutine
     * cancellation still propagates).
     */
    suspend fun refreshEntitlement(): RefreshEntitlementOutcome

    /**
     * Fire-and-forget Play sync failures as values (CODE_STYLE_RULES
     * § "Partial success"). Repository implementations never log; the app-shell
     * boundary drains each diagnostic through `LoggingPort` exactly once.
     */
    fun observeEntitlementDiagnostics(): Flow<EntitlementDiagnostic>
}
