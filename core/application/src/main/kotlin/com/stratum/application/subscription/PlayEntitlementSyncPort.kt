package com.stratum.application.subscription

/**
 * Client port for the `syncPlayEntitlement` callable. Verifies a Play purchase token
 * server-side and writes the Play entitlement snapshot. Implementations must never log
 * the raw purchase token.
 */
interface PlayEntitlementSyncPort {
    suspend fun syncPurchaseToken(purchaseToken: String): Result<Boolean>
}
