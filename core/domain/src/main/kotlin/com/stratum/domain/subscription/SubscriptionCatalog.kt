package com.stratum.domain.subscription


object SubscriptionCatalog {
    const val ANNUAL_PRODUCT_ID = "stratum_membership_annual"
    const val MONTHLY_PRODUCT_ID = "stratum_membership_monthly"

    val knownProductIds: Set<String> = setOf(ANNUAL_PRODUCT_ID, MONTHLY_PRODUCT_ID)

    fun isKnownProductId(id: String): Boolean = id in knownProductIds

    fun purchaseHasKnownProduct(purchase: SubscriptionPurchase): Boolean =
        purchase.productIds.any(::isKnownProductId)
}
