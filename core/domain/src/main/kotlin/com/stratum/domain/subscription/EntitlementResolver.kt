package com.stratum.domain.subscription

import com.stratum.domain.auth.AuthState

fun resolveEntitlement(
    auth: AuthState,
    complimentary: Boolean,
    purchases: List<SubscriptionPurchase>,
): EntitlementState = when (auth) {
    AuthState.Loading -> EntitlementState.Loading
    AuthState.Unauthenticated,
    is AuthState.Error -> EntitlementState.NotEntitled("Not authenticated")
    is AuthState.Authenticated -> when {
        complimentary -> EntitlementState.Entitled(
            source = EntitlementSource.ComplimentaryAccess,
        )
        purchases.any(SubscriptionCatalog::purchaseHasKnownProduct) -> EntitlementState.Entitled(
            source = EntitlementSource.GooglePlaySubscription,
        )
        else -> EntitlementState.NotEntitled()
    }
}
