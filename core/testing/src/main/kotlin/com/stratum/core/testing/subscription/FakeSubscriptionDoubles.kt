package com.stratum.core.testing.subscription

import com.stratum.application.subscription.BillingPort
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.application.subscription.EntitlementRepository
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementState
import com.stratum.application.subscription.PlayEntitlementSyncPort
import com.stratum.domain.subscription.SubscriptionCatalog
import com.stratum.domain.subscription.SubscriptionProduct
import com.stratum.domain.subscription.SubscriptionPurchase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow

class FakeBillingPort(
    initialPurchases: List<SubscriptionPurchase> = emptyList(),
) : BillingPort {
    private val purchasesState = MutableStateFlow(initialPurchases)
    private val purchaseResultsFlow = MutableSharedFlow<BillingPurchaseResult>(extraBufferCapacity = 16)

    override val activePurchases: Flow<List<SubscriptionPurchase>> = purchasesState.asStateFlow()
    override val purchaseResults: Flow<BillingPurchaseResult> = purchaseResultsFlow.asSharedFlow()

    var availableProducts: List<SubscriptionProduct> = listOf(
        SubscriptionProduct(
            productId = SubscriptionCatalog.ANNUAL_PRODUCT_ID,
            basePlanId = "annual-autorenewing",
            offerId = "free-trial-14-days",
            formattedPrice = "$49.99",
            priceAmountMicros = 49_990_000L,
            currencyCode = "USD",
            billingPeriodIso = "P1Y",
            freeTrialDays = 14,
            title = "Stratum Membership (Annual)",
            description = "Annual membership with 14-day free trial",
            offerToken = "fake-annual-offer-token",
        ),
        SubscriptionProduct(
            productId = SubscriptionCatalog.MONTHLY_PRODUCT_ID,
            basePlanId = "monthly-autorenewing",
            offerId = null,
            formattedPrice = "$7.99",
            priceAmountMicros = 7_990_000L,
            currencyCode = "USD",
            billingPeriodIso = "P1M",
            freeTrialDays = 0,
            title = "Stratum Membership (Monthly)",
            description = "Monthly membership",
            offerToken = "fake-monthly-offer-token",
        ),
    )

    /** When non-null, the corresponding operation throws this error. */
    var launchBillingFailure: Throwable? = null
    var restorePurchasesFailure: Throwable? = null
    var lastLaunchedProduct: SubscriptionProduct? = null
    var queryException: Throwable? = null

    fun setPurchases(purchases: List<SubscriptionPurchase>) {
        purchasesState.value = purchases
    }

    fun emitPurchaseResult(result: BillingPurchaseResult) {
        purchaseResultsFlow.tryEmit(result)
    }

    override suspend fun querySubscriptionProducts(): List<SubscriptionProduct> {
        queryException?.let { throw it }
        return availableProducts
    }

    override suspend fun launchBillingFlow(product: SubscriptionProduct) {
        lastLaunchedProduct = product
        launchBillingFailure?.let { throw it }
    }

    override suspend fun restorePurchases() {
        restorePurchasesFailure?.let { throw it }
    }
}

class FakeEntitlementRepository(
    initialState: EntitlementState = EntitlementState.NotEntitled(),
) : EntitlementRepository {
    private val state = MutableStateFlow(initialState)
    private val failures = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)

    var throwOnSubscribe: Throwable? = null

    fun setState(next: EntitlementState) {
        state.value = next
    }

    fun failCollector(error: Throwable) {
        check(failures.tryEmit(error)) { "Failed to emit entitlement collector failure" }
    }

    // Diagnostics must never be silently dropped for capacity: the single app-boundary
    // drain starts at process start and sync failures are rare.
    private val diagnosticsChannel = Channel<EntitlementDiagnostic>(Channel.UNLIMITED)

    fun emitDiagnostic(diagnostic: EntitlementDiagnostic) {
        check(diagnosticsChannel.trySend(diagnostic).isSuccess) { "Failed to emit entitlement diagnostic" }
    }

    override fun observeEntitlement(): Flow<EntitlementState> =
        flow {
            throwOnSubscribe?.let { error ->
                throwOnSubscribe = null
                throw error
            }
            emitAll(
                merge(
                    state,
                    failures.map { error -> throw error },
                ),
            )
        }

    /** When non-null, [refreshEntitlement] returns this state instead of the current one. */
    var refreshResult: EntitlementState? = null

    /** When non-null, [refreshEntitlement] throws this error. */
    var refreshFailure: Throwable? = null

    override suspend fun refreshEntitlement(): EntitlementState {
        refreshFailure?.let { throw it }
        return refreshResult ?: state.value
    }

    override fun observeEntitlementDiagnostics(): Flow<EntitlementDiagnostic> =
        diagnosticsChannel.receiveAsFlow()
}

class FakePlayEntitlementSyncPort : PlayEntitlementSyncPort {
    val syncedTokens = mutableListOf<String>()
    var syncValue: Boolean = true

    /** When non-null, [syncPurchaseToken] throws this error. */
    var syncFailure: Throwable? = null

    override suspend fun syncPurchaseToken(purchaseToken: String): Boolean {
        syncedTokens += purchaseToken
        syncFailure?.let { throw it }
        return syncValue
    }
}
