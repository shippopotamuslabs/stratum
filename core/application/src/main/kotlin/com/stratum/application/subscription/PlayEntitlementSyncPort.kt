package com.stratum.application.subscription

import com.stratum.domain.subscription.EntitlementDiagnostic

/** Outcome of syncing a Play purchase token at the [PlayEntitlementSyncPort]. */
sealed interface PlayEntitlementSyncOutcome {
    data class Success(val entitled: Boolean) : PlayEntitlementSyncOutcome

    data class Failed(
        val diagnostics: List<EntitlementDiagnostic>,
    ) : PlayEntitlementSyncOutcome {
        init {
            require(diagnostics.isNotEmpty()) {
                "PlayEntitlementSyncOutcome.Failed requires at least one diagnostic."
            }
        }
    }
}

/**
 * Client port for the `syncPlayEntitlement` callable. Verifies a Play purchase token
 * server-side and writes the Play entitlement snapshot. Implementations must never log
 * the raw purchase token. An operational failure is the outcome's Failed branch
 * (STR-243 remediation) — its [EntitlementDiagnostic.PlaySyncFailed] rides as a
 * value for the caller's exactly-once drain; cancellation still propagates.
 */
interface PlayEntitlementSyncPort {
    suspend fun syncPurchaseToken(purchaseToken: String): PlayEntitlementSyncOutcome
}
