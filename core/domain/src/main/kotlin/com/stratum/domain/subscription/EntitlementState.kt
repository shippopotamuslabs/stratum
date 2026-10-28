package com.stratum.domain.subscription

import com.stratum.domain.auth.AuthState

enum class EntitlementSource {
    GooglePlaySubscription,
    ComplimentaryAccess,
}

sealed interface EntitlementState {
    data object Loading : EntitlementState

    data class Entitled(
        val source: EntitlementSource,
        val isFreeTrial: Boolean = false,
        val expiresAtEpochMillis: Long? = null,
    ) : EntitlementState

    data class NotEntitled(
        val reason: String? = null,
    ) : EntitlementState

    companion object {
        /**
         * The entitlement decision, owned by the state type: entitlement is derived from
         * auth + complimentary access + purchases, never stored raw and never re-derived
         * by consumers. A null [NotEntitled.reason] is a plain not-entitled outcome
         * rather than an error.
         */
        fun resolve(
            auth: AuthState,
            complimentary: Boolean,
            purchases: List<SubscriptionPurchase>,
        ): EntitlementState = when (auth) {
            AuthState.Loading -> Loading
            AuthState.Unauthenticated,
            is AuthState.Error -> NotEntitled("Not authenticated")
            is AuthState.Authenticated -> when {
                complimentary -> Entitled(
                    source = EntitlementSource.ComplimentaryAccess,
                )
                purchases.any(SubscriptionCatalog::purchaseHasKnownProduct) -> Entitled(
                    source = EntitlementSource.GooglePlaySubscription,
                )
                else -> NotEntitled()
            }
        }
    }
}
