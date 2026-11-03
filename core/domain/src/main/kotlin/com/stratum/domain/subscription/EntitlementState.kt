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
         *
         * [ComplimentaryAccess.Unknown] means the override read failed, which is not
         * evidence of no access: with no qualifying Play purchase the outcome stays
         * [Loading] instead of paywalling the user on a transient read failure (STR-280).
         */
        fun resolve(
            auth: AuthState,
            complimentary: ComplimentaryAccess,
            purchases: List<SubscriptionPurchase>,
        ): EntitlementState = when (auth) {
            AuthState.Loading -> Loading
            AuthState.Unauthenticated,
            is AuthState.Error -> NotEntitled("Not authenticated")
            is AuthState.Authenticated -> when {
                complimentary == ComplimentaryAccess.Granted -> Entitled(
                    source = EntitlementSource.ComplimentaryAccess,
                )
                purchases.any(SubscriptionCatalog::purchaseHasKnownProduct) -> Entitled(
                    source = EntitlementSource.GooglePlaySubscription,
                )
                complimentary == ComplimentaryAccess.Unknown -> Loading
                else -> NotEntitled()
            }
        }
    }
}
