package com.stratum.application.subscription

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.SubscriptionProduct

/** Shared log tag for entitlement boundary drains. */
const val SUBSCRIPTION_REPO_TAG = "SubscriptionRepo"

/**
 * Outcome of loading the subscription product catalog (STR-243). The
 * originating throwable is logged once via [LoggingPort.error] at the
 * use-case boundary; it never crosses as a payload.
 */
sealed interface GetSubscriptionProductsResult {
    data class Success(val products: List<SubscriptionProduct>) : GetSubscriptionProductsResult

    data object Failed : GetSubscriptionProductsResult
}

/**
 * Outcome of launching the billing flow (STR-243). [Failed] carries the
 * adapter's message where one exists, following the shipped
 * [BillingPurchaseResult.Failed] pattern — presentation falls back to its own
 * copy when it is null.
 */
sealed interface PurchaseSubscriptionResult {
    data object Success : PurchaseSubscriptionResult

    data class Failed(val message: String?) : PurchaseSubscriptionResult
}

/** Outcome of restoring purchases (STR-243); same message pattern as [PurchaseSubscriptionResult]. */
sealed interface RestorePurchasesResult {
    data class Success(val entitlement: EntitlementState) : RestorePurchasesResult

    data class Failed(val message: String?) : RestorePurchasesResult
}

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
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): GetSubscriptionProductsResult =
        try {
            GetSubscriptionProductsResult.Success(billingPort.querySubscriptionProducts())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to load subscription products: ${error.message}", error)
            GetSubscriptionProductsResult.Failed
        }

    private companion object {
        const val TAG = "GetSubscriptionProducts"
    }
}

class PurchaseSubscriptionUseCase @Inject constructor(
    private val billingPort: BillingPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(product: SubscriptionProduct): PurchaseSubscriptionResult =
        try {
            billingPort.launchBillingFlow(product)
            PurchaseSubscriptionResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to launch billing flow: ${error.message}", error)
            PurchaseSubscriptionResult.Failed(error.message)
        }

    private companion object {
        const val TAG = "PurchaseSubscription"
    }
}

class RestorePurchasesUseCase @Inject constructor(
    private val billingPort: BillingPort,
    private val repository: EntitlementRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): RestorePurchasesResult =
        try {
            billingPort.restorePurchases()
            RestorePurchasesResult.Success(repository.refreshEntitlement())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to restore purchases: ${error.message}", error)
            RestorePurchasesResult.Failed(error.message)
        }

    private companion object {
        const val TAG = "RestorePurchases"
    }
}

class ObserveBillingPurchaseResultsUseCase @Inject constructor(
    private val billingPort: BillingPort,
) {
    operator fun invoke(): Flow<BillingPurchaseResult> = billingPort.purchaseResults
}
