package com.stratum.feature.subscription

/** Presentation representation of a subscription product for the paywall. */
data class PaywallProductUi(
    val productId: String,
    val formattedPrice: String,
    val pricePerMonthText: String,
    val freeTrialDays: Int,
    val billingPeriodText: String,
    val badge: String? = null,
    val title: String,
    val description: String,
    val isAnnual: Boolean,
)

sealed interface PaywallUiState {
    data object Loading : PaywallUiState

    data class Content(
        val products: List<PaywallProductUi>,
        val selectedProductId: String,
        val isPurchasing: Boolean = false,
        val isRestoring: Boolean = false,
        val isEntitled: Boolean = false,
        val isComplimentary: Boolean = false,
        val errorMessage: String? = null,
        val userMessage: String? = null,
    ) : PaywallUiState {
        val selectedProduct: PaywallProductUi?
            get() = products.firstOrNull { it.productId == selectedProductId }
                ?: products.firstOrNull()
    }

    data class Error(
        val message: String,
    ) : PaywallUiState
}

sealed interface PaywallUiEvent {
    data object PurchaseSuccess : PaywallUiEvent
    data class ShowMessage(val message: String) : PaywallUiEvent
}

internal object PaywallCopy {
    const val LOAD_ERROR =
        "Couldn't load subscription plans. Check your connection and try again."
    const val RESTORE_NONE = "No active subscriptions found"
    const val RESTORE_SUCCESS = "Purchases restored successfully"
    const val RESTORE_FAILED = "Failed restoring purchases"
    const val PURCHASE_PENDING =
        "Your purchase is pending. You'll get access when Google Play confirms it."
    const val PURCHASE_FAILED = "Purchase flow failed"
}
