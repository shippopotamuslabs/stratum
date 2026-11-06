package com.stratum.core.testing.subscription

import com.stratum.application.subscription.BillingDiagnostic
import com.stratum.application.subscription.BillingFlowOutcome
import com.stratum.application.subscription.BillingPort
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.application.subscription.EntitlementRepository
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementState
import com.stratum.application.subscription.PlayEntitlementSyncOutcome
import com.stratum.application.subscription.PlayEntitlementSyncPort
import com.stratum.application.subscription.RefreshEntitlementOutcome
import com.stratum.application.subscription.RestorePurchasesOutcome
import com.stratum.application.subscription.SubscriptionProductsOutcome
import kotlinx.coroutines.CancellationException
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

    /**
     * When non-null, the corresponding operation returns its Failed outcome
     * carrying this error as the diagnostic cause. A [CancellationException]
     * still throws — cancellation is never an outcome branch.
     */
    var launchBillingFailure: Throwable? = null
    var restorePurchasesFailure: Throwable? = null
    var lastLaunchedProduct: SubscriptionProduct? = null
    var queryException: Throwable? = null

    private fun failureOutcome(failure: Throwable): List<BillingDiagnostic> {
        if (failure is CancellationException) throw failure
        return listOf(BillingDiagnostic.OperationFailed(failure))
    }

    fun setPurchases(purchases: List<SubscriptionPurchase>) {
        purchasesState.value = purchases
    }

    fun emitPurchaseResult(result: BillingPurchaseResult) {
        purchaseResultsFlow.tryEmit(result)
    }

    override suspend fun querySubscriptionProducts(): SubscriptionProductsOutcome {
        queryException?.let { failure ->
            return SubscriptionProductsOutcome.Failed(failure.message, failureOutcome(failure))
        }
        return SubscriptionProductsOutcome.Success(availableProducts)
    }

    override suspend fun launchBillingFlow(product: SubscriptionProduct): BillingFlowOutcome {
        lastLaunchedProduct = product
        launchBillingFailure?.let { failure ->
            return BillingFlowOutcome.Failed(failure.message, failureOutcome(failure))
        }
        return BillingFlowOutcome.Success
    }

    override suspend fun restorePurchases(): RestorePurchasesOutcome {
        restorePurchasesFailure?.let { failure ->
            return RestorePurchasesOutcome.Failed(failure.message, failureOutcome(failure))
        }
        return RestorePurchasesOutcome.Success
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

    /** When non-null, [refreshEntitlement] returns Failed carrying this as the diagnostic cause. */
    var refreshFailure: Throwable? = null

    override suspend fun refreshEntitlement(): RefreshEntitlementOutcome {
        refreshFailure?.let { failure ->
            if (failure is CancellationException) throw failure
            return RefreshEntitlementOutcome.Failed(
                listOf(BillingDiagnostic.OperationFailed(failure)),
            )
        }
        return RefreshEntitlementOutcome.Success(refreshResult ?: state.value)
    }

    override fun observeEntitlementDiagnostics(): Flow<EntitlementDiagnostic> =
        diagnosticsChannel.receiveAsFlow()
}

class FakePlayEntitlementSyncPort : PlayEntitlementSyncPort {
    val syncedTokens = mutableListOf<String>()
    var syncValue: Boolean = true

    /** When non-null, [syncPurchaseToken] returns Failed carrying this as a PlaySyncFailed diagnostic. */
    var syncFailure: Throwable? = null

    override suspend fun syncPurchaseToken(
        purchaseToken: String,
    ): PlayEntitlementSyncOutcome {
        syncedTokens += purchaseToken
        syncFailure?.let { failure ->
            if (failure is CancellationException) throw failure
            return PlayEntitlementSyncOutcome.Failed(
                listOf(EntitlementDiagnostic.PlaySyncFailed(failure)),
            )
        }
        return PlayEntitlementSyncOutcome.Success(syncValue)
    }
}
