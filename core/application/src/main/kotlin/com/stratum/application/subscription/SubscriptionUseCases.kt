package com.stratum.application.subscription

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.SubscriptionProduct

/** Shared log tag for entitlement boundary drains. */
const val SUBSCRIPTION_REPO_TAG = "SubscriptionRepo"

class ObserveEntitlementUseCase @Inject constructor(
    private val repository: EntitlementRepository,
) {
    operator fun invoke(): Flow<EntitlementState> =
        repository.observeEntitlement()
}

class ObserveEntitlementDiagnosticsUseCase @Inject constructor(
    private val repository: EntitlementRepository,
) {
    operator fun invoke(): Flow<EntitlementDiagnostic> =
        repository.observeEntitlementDiagnostics()
}

class GetSubscriptionProductsUseCase @Inject constructor(
    private val billingPort: BillingPort,
) {
    suspend operator fun invoke(): Result<List<SubscriptionProduct>> =
        billingPort.querySubscriptionProducts()
}

class PurchaseSubscriptionUseCase @Inject constructor(
    private val billingPort: BillingPort,
) {
    suspend operator fun invoke(product: SubscriptionProduct): Result<Unit> =
        billingPort.launchBillingFlow(product)
}

class RestorePurchasesUseCase @Inject constructor(
    private val billingPort: BillingPort,
    private val repository: EntitlementRepository,
) {
    suspend operator fun invoke(): Result<EntitlementState> =
        billingPort.restorePurchases().fold(
            onSuccess = { repository.refreshEntitlement() },
            onFailure = { Result.failure(it) },
        )
}

class ObserveBillingPurchaseResultsUseCase @Inject constructor(
    private val billingPort: BillingPort,
) {
    operator fun invoke(): Flow<BillingPurchaseResult> = billingPort.purchaseResults
}
