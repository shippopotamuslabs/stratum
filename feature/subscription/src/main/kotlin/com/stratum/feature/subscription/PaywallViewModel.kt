package com.stratum.feature.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.GetSubscriptionProductsUseCase
import com.stratum.domain.subscription.ObserveBillingPurchaseResultsUseCase
import com.stratum.domain.subscription.ObserveEntitlementUseCase
import com.stratum.domain.subscription.PurchaseSubscriptionUseCase
import com.stratum.domain.subscription.RestorePurchasesUseCase
import com.stratum.domain.subscription.SubscriptionCatalog
import com.stratum.domain.subscription.SubscriptionProduct
import com.stratum.domain.subscription.annualSavingsPercent
import com.stratum.domain.subscription.monthlyEquivalentMicros
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.logging.loggedViewModelScope

private data class TransientState(
    val products: List<SubscriptionProduct> = emptyList(),
    val selectedProductId: String? = null,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val errorMessage: String? = null,
    val loadError: String? = null,
    val userMessage: String? = null,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val getSubscriptionProducts: GetSubscriptionProductsUseCase,
    private val purchaseSubscription: PurchaseSubscriptionUseCase,
    private val restorePurchases: RestorePurchasesUseCase,
    observeEntitlement: ObserveEntitlementUseCase,
    observeBillingPurchaseResults: ObserveBillingPurchaseResultsUseCase,
    logError: LogErrorUseCase = LogErrorUseCase(com.stratum.domain.logging.SilentLoggingPort),
) : ViewModel(loggedViewModelScope("PaywallViewModel", logError)) {

    private val transientState = MutableStateFlow(TransientState())

    private val _events = Channel<PaywallUiEvent>(Channel.BUFFERED)
    val events: Flow<PaywallUiEvent> = _events.receiveAsFlow()

    private val entitlementState: StateFlow<EntitlementState> =
        observeEntitlement().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EntitlementState.Loading,
        )

    val uiState: StateFlow<PaywallUiState> =
        combine(transientState, entitlementState) { transient, entitlement ->
            when {
                transient.loadError != null -> PaywallUiState.Error(
                    message = transient.loadError,
                )
                transient.products.isEmpty() || entitlement is EntitlementState.Loading -> {
                    PaywallUiState.Loading
                }
                else -> {
                    val isEntitled = entitlement is EntitlementState.Entitled
                    val isComplimentary = entitlement is EntitlementState.Entitled &&
                        entitlement.source == EntitlementSource.ComplimentaryAccess
                    val productUis = mapProducts(transient.products)
                    val currentSelectedId = transient.selectedProductId
                        ?: productUis.firstOrNull { it.isAnnual }?.productId
                        ?: productUis.first().productId
                    PaywallUiState.Content(
                        products = productUis,
                        selectedProductId = currentSelectedId,
                        isPurchasing = transient.isPurchasing,
                        isRestoring = transient.isRestoring,
                        isEntitled = isEntitled,
                        isComplimentary = isComplimentary,
                        errorMessage = transient.errorMessage,
                        userMessage = transient.userMessage,
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PaywallUiState.Loading,
        )

    init {
        loadProductDetails()
        collectEntitlement()
        collectPurchaseResults(observeBillingPurchaseResults)
    }

    private fun collectEntitlement() {
        viewModelScope.launch {
            entitlementState.collect { entitlement ->
                if (transientState.value.isPurchasing && entitlement is EntitlementState.Entitled) {
                    completePurchaseSuccess()
                }
            }
        }
    }

    private fun collectPurchaseResults(
        observeBillingPurchaseResults: ObserveBillingPurchaseResultsUseCase,
    ) {
        viewModelScope.launch {
            observeBillingPurchaseResults().collect { result ->
                when (result) {
                    BillingPurchaseResult.Canceled -> {
                        transientState.update { it.copy(isPurchasing = false) }
                    }
                    BillingPurchaseResult.Pending -> {
                        transientState.update {
                            it.copy(
                                isPurchasing = false,
                                userMessage = PaywallCopy.PURCHASE_PENDING,
                            )
                        }
                    }
                    is BillingPurchaseResult.Failed -> {
                        val message = result.message.ifBlank { PaywallCopy.PURCHASE_FAILED }
                        transientState.update {
                            it.copy(isPurchasing = false, errorMessage = message)
                        }
                    }
                    BillingPurchaseResult.Purchased -> {
                        if (entitlementState.value is EntitlementState.Entitled) {
                            completePurchaseSuccess()
                        }
                    }
                }
            }
        }
    }

    private suspend fun completePurchaseSuccess() {
        val wasPurchasing = transientState.value.isPurchasing
        transientState.update { it.copy(isPurchasing = false) }
        if (wasPurchasing) {
            _events.send(PaywallUiEvent.PurchaseSuccess)
        }
    }

    fun loadProductDetails() {
        viewModelScope.launch {
            transientState.update {
                it.copy(loadError = null, products = emptyList(), selectedProductId = null)
            }
            getSubscriptionProducts()
                .onSuccess { products ->
                    if (products.isEmpty()) {
                        transientState.update { it.copy(loadError = PaywallCopy.LOAD_ERROR) }
                    } else {
                        val annualId = products.firstOrNull(::isAnnualProduct)?.productId
                            ?: products.first().productId
                        transientState.update {
                            it.copy(
                                products = products,
                                selectedProductId = annualId,
                                loadError = null,
                            )
                        }
                    }
                }
                .onFailure {
                    transientState.update { it.copy(loadError = PaywallCopy.LOAD_ERROR) }
                }
        }
    }

    fun onSelectProduct(productId: String) {
        transientState.update { it.copy(selectedProductId = productId) }
    }

    fun onPurchaseClicked() {
        val selectedId = transientState.value.selectedProductId
        val product = transientState.value.products.firstOrNull { it.productId == selectedId }
            ?: transientState.value.products.firstOrNull()
            ?: return

        viewModelScope.launch {
            transientState.update {
                it.copy(isPurchasing = true, errorMessage = null, userMessage = null)
            }
            purchaseSubscription(product).onFailure { error ->
                transientState.update {
                    it.copy(
                        isPurchasing = false,
                        errorMessage = error.message ?: PaywallCopy.PURCHASE_FAILED,
                    )
                }
            }
        }
    }

    fun onRestorePurchasesClicked() {
        viewModelScope.launch {
            transientState.update {
                it.copy(isRestoring = true, errorMessage = null, userMessage = null)
            }
            restorePurchases()
                .onSuccess { entitlement ->
                    if (entitlement is EntitlementState.Entitled) {
                        transientState.update {
                            it.copy(isRestoring = false, userMessage = PaywallCopy.RESTORE_SUCCESS)
                        }
                        _events.send(PaywallUiEvent.PurchaseSuccess)
                        _events.send(PaywallUiEvent.ShowMessage(PaywallCopy.RESTORE_SUCCESS))
                    } else {
                        transientState.update {
                            it.copy(isRestoring = false, userMessage = PaywallCopy.RESTORE_NONE)
                        }
                        _events.send(PaywallUiEvent.ShowMessage(PaywallCopy.RESTORE_NONE))
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: PaywallCopy.RESTORE_FAILED
                    transientState.update {
                        it.copy(isRestoring = false, errorMessage = message, userMessage = message)
                    }
                    _events.send(PaywallUiEvent.ShowMessage(message))
                }
        }
    }

    fun clearError() {
        transientState.update { it.copy(errorMessage = null) }
    }

    fun userMessageShown() {
        transientState.update { it.copy(userMessage = null) }
    }
}

internal fun mapProducts(products: List<SubscriptionProduct>): List<PaywallProductUi> {
    val monthlyPriceMicros = products.firstOrNull(::isMonthlyProduct)?.priceAmountMicros
    return products.map { prod ->
        val annual = isAnnualProduct(prod)
        val periodText = billingPeriodLabel(prod.billingPeriodIso)
        val savingsPercent = if (annual && monthlyPriceMicros != null) {
            val annualMonthly = monthlyEquivalentMicros(
                prod.priceAmountMicros,
                prod.billingPeriodIso,
            )
            annualMonthly?.let { annualSavingsPercent(it, monthlyPriceMicros) }
        } else {
            null
        }
        PaywallProductUi(
            productId = prod.productId,
            formattedPrice = prod.formattedPrice,
            pricePerMonthText = formatPaywallPricePerMonth(
                formattedPrice = prod.formattedPrice,
                priceAmountMicros = prod.priceAmountMicros,
                currencyCode = prod.currencyCode,
                billingPeriodIso = prod.billingPeriodIso,
                billingPeriodText = periodText,
            ),
            freeTrialDays = prod.freeTrialDays,
            billingPeriodText = periodText,
            badge = savingsPercent?.takeIf { it > 0 }?.let { "SAVE $it%" },
            title = if (annual) "Annual Plan" else "Monthly Plan",
            description = if (annual) {
                "Billed annually at ${prod.formattedPrice}"
            } else {
                "Billed monthly at ${prod.formattedPrice}"
            },
            isAnnual = annual,
        )
    }
}

internal fun isAnnualProduct(product: SubscriptionProduct): Boolean =
    product.productId == SubscriptionCatalog.ANNUAL_PRODUCT_ID ||
        product.billingPeriodIso.equals("P1Y", ignoreCase = true)

internal fun isMonthlyProduct(product: SubscriptionProduct): Boolean =
    product.productId == SubscriptionCatalog.MONTHLY_PRODUCT_ID ||
        product.billingPeriodIso.equals("P1M", ignoreCase = true)

internal fun billingPeriodLabel(billingPeriodIso: String): String =
    when (billingPeriodIso.trim().uppercase()) {
        "P1Y" -> "year"
        "P1M" -> "month"
        else -> billingPeriodIso
    }

internal fun formatPaywallPricePerMonth(
    formattedPrice: String,
    priceAmountMicros: Long,
    currencyCode: String,
    billingPeriodIso: String,
    billingPeriodText: String,
): String {
    val monthly = monthlyEquivalentMicros(priceAmountMicros, billingPeriodIso)
        ?: return "$formattedPrice / $billingPeriodText"
    val formatted = formatMicrosAsCurrency(monthly, currencyCode)
        ?: return "$formattedPrice / $billingPeriodText"
    return "$formatted / mo"
}

internal fun formatMicrosAsCurrency(micros: Long, currencyCode: String): String? {
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull() ?: return null
    val formatter = NumberFormat.getCurrencyInstance()
    formatter.currency = currency
    return formatter.format(micros / MICROS_PER_CURRENCY_UNIT)
}

private const val MICROS_PER_CURRENCY_UNIT = 1_000_000.0
