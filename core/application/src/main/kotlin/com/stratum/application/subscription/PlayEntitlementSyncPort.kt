package com.stratum.application.subscription

/**
 * Client port for the `syncPlayEntitlement` callable. Verifies a Play purchase token
 * server-side and writes the Play entitlement snapshot. Implementations must never log
 * the raw purchase token. An operational failure throws to the caller (STR-243).
 */
interface PlayEntitlementSyncPort {
    suspend fun syncPurchaseToken(purchaseToken: String): Boolean
}
