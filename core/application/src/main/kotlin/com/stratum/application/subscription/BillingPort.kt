package com.stratum.application.subscription

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.SubscriptionProduct
import com.stratum.domain.subscription.SubscriptionPurchase

/**
 * Google Play Billing boundary (STR-243). Operations are plain suspend calls:
 * an operational failure throws to the caller, whose use-case boundary logs it
 * once and translates it into its sealed outcome — no raw-`Result` failure
 * payload crosses the port.
 */
interface BillingPort {
    val activePurchases: Flow<List<SubscriptionPurchase>>

    val purchaseResults: Flow<BillingPurchaseResult>

    suspend fun querySubscriptionProducts(): List<SubscriptionProduct>

    suspend fun launchBillingFlow(product: SubscriptionProduct)

    suspend fun restorePurchases()
}
