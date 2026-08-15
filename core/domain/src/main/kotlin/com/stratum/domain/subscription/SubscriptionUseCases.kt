package com.stratum.domain.subscription

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveEntitlementUseCase @Inject constructor(
    private val repository: EntitlementRepository,
) {
    operator fun invoke(): Flow<EntitlementState> = repository.observeEntitlement()
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
    suspend operator fun invoke(): Result<EntitlementState> = runCatching {
        billingPort.restorePurchases().getOrThrow()
        repository.refreshEntitlement().getOrThrow()
    }
}

class ObserveBillingPurchaseResultsUseCase @Inject constructor(
    private val billingPort: BillingPort,
) {
    operator fun invoke(): Flow<BillingPurchaseResult> = billingPort.purchaseResults
}
