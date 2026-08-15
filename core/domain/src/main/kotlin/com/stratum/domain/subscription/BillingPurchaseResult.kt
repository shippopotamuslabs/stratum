package com.stratum.domain.subscription

sealed interface BillingPurchaseResult {
    data object Purchased : BillingPurchaseResult

    data object Canceled : BillingPurchaseResult

    data object Pending : BillingPurchaseResult

    data class Failed(
        val message: String,
        val responseCode: Int,
    ) : BillingPurchaseResult
}
