package com.stratum.application.subscription

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.SubscriptionProduct
import com.stratum.domain.subscription.SubscriptionPurchase

interface BillingPort {
    val activePurchases: Flow<List<SubscriptionPurchase>>

    val purchaseResults: Flow<BillingPurchaseResult>

    suspend fun querySubscriptionProducts(): Result<List<SubscriptionProduct>>

    suspend fun launchBillingFlow(product: SubscriptionProduct): Result<Unit>

    suspend fun restorePurchases(): Result<Unit>
}
