package com.stratum.core.testing.subscription

import com.stratum.application.subscription.BillingPort
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.application.subscription.EntitlementRepository
import com.stratum.domain.subscription.EntitlementState
import com.stratum.application.subscription.PlayEntitlementSyncPort
import com.stratum.domain.subscription.SubscriptionCatalog
import com.stratum.domain.subscription.SubscriptionProduct
import com.stratum.domain.subscription.SubscriptionPurchase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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

    var launchBillingResult: Result<Unit> = Result.success(Unit)
    var restorePurchasesResult: Result<Unit> = Result.success(Unit)
    var lastLaunchedProduct: SubscriptionProduct? = null
    var queryException: Throwable? = null

    fun setPurchases(purchases: List<SubscriptionPurchase>) {
        purchasesState.value = purchases
    }

    fun emitPurchaseResult(result: BillingPurchaseResult) {
        purchaseResultsFlow.tryEmit(result)
    }

    override suspend fun querySubscriptionProducts(): Result<List<SubscriptionProduct>> {
        queryException?.let { return Result.failure(it) }
        return Result.success(availableProducts)
    }

    override suspend fun launchBillingFlow(product: SubscriptionProduct): Result<Unit> {
        lastLaunchedProduct = product
        return launchBillingResult
    }

    override suspend fun restorePurchases(): Result<Unit> = restorePurchasesResult
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

    var refreshResult: Result<EntitlementState>? = null

    override suspend fun refreshEntitlement(): Result<EntitlementState> =
        refreshResult ?: Result.success(state.value)
}

class FakePlayEntitlementSyncPort : PlayEntitlementSyncPort {
    val syncedTokens = mutableListOf<String>()
    var syncResult: Result<Boolean> = Result.success(true)

    override suspend fun syncPurchaseToken(purchaseToken: String): Result<Boolean> {
        syncedTokens += purchaseToken
        return syncResult
    }
}
