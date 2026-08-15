package com.stratum.domain.subscription

data class SubscriptionProduct(
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val billingPeriodIso: String,
    val freeTrialDays: Int,
    val title: String,
    val description: String,
    val offerToken: String,
)

data class SubscriptionPurchase(
    val orderId: String?,
    val purchaseToken: String,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val purchaseTimeEpochMillis: Long,
    val productIds: List<String>,
)
