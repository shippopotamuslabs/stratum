package com.stratum.domain.subscription

import kotlinx.coroutines.flow.Flow

interface BillingPort {
    val activePurchases: Flow<List<SubscriptionPurchase>>

    val purchaseResults: Flow<BillingPurchaseResult>

    suspend fun querySubscriptionProducts(): Result<List<SubscriptionProduct>>

    suspend fun launchBillingFlow(product: SubscriptionProduct): Result<Unit>

    suspend fun restorePurchases(): Result<Unit>
}
